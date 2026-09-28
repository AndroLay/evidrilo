package dev.nextgen.mobile.projectcatalog

import kotlin.coroutines.cancellation.CancellationException

internal data class StudentProjectZipEntry(
    val name: String,
    val method: Int,
    val flags: Int,
    val crc32: Long,
    val compressedSize: Long,
    val expandedSize: Long,
    val isDirectory: Boolean = false,
)

internal sealed interface StudentProjectZipPreflightResult {
    data class Valid(val entries: Map<String, StudentProjectZipEntry>) : StudentProjectZipPreflightResult
    data class Rejected(val code: String) : StudentProjectZipPreflightResult
}

/** Inspects ZIP metadata before the streaming reader is allowed to expand an entry. */
internal object StudentProjectArchiveZipPreflight {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val LOCAL_SIGNATURE = 0x04034b50L
    private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
    private const val MAX_COMMENT_LENGTH = 65_535
    private const val ALLOWED_FLAGS = 0x0808 // UTF-8 name and optional data descriptor only.

    fun inspect(
        bytes: ByteArray,
        maxEntries: Int,
        maxExpandedBytes: Long,
        maxCompressionRatio: Long,
        entryLimit: (String) -> Int,
        allowDirectories: Boolean = false,
        ensureActive: () -> Unit = {},
    ): StudentProjectZipPreflightResult {
        ensureActive()
        if (bytes.size < 22) return rejected("PROJECT_ARCHIVE_INVALID_ZIP")
        val eocd = findEocd(bytes) ?: return rejected("PROJECT_ARCHIVE_INVALID_ZIP")
        try {
            if (bytes.u16(eocd + 4) != 0 || bytes.u16(eocd + 6) != 0) {
                return rejected("PROJECT_ARCHIVE_MULTIDISK_UNSUPPORTED")
            }
            val diskEntries = bytes.u16(eocd + 8)
            val totalEntries = bytes.u16(eocd + 10)
            val directorySize = bytes.u32(eocd + 12)
            val directoryOffset = bytes.u32(eocd + 16)
            val commentLength = bytes.u16(eocd + 20)
            if (eocd + 22L + commentLength != bytes.size.toLong() ||
                diskEntries != totalEntries || totalEntries == 0 || totalEntries > maxEntries ||
                totalEntries == 0xffff || directorySize == 0xffff_ffffL || directoryOffset == 0xffff_ffffL ||
                directoryOffset + directorySize != eocd.toLong()
            ) {
                return rejected("PROJECT_ARCHIVE_DIRECTORY_INVALID")
            }

            val entries = linkedMapOf<String, StudentProjectZipEntry>()
            val seenNames = mutableSetOf<String>()
            val dataRanges = mutableListOf<LongRange>()
            var expandedTotal = 0L
            var cursor = directoryOffset.toInt()
            repeat(totalEntries) {
                ensureActive()
                bytes.requireRange(cursor, 46, eocd)
                if (bytes.u32(cursor) != CENTRAL_SIGNATURE) return rejected("PROJECT_ARCHIVE_DIRECTORY_INVALID")
                val versionMadeBy = bytes.u16(cursor + 4)
                val flags = bytes.u16(cursor + 8)
                val method = bytes.u16(cursor + 10)
                val crc = bytes.u32(cursor + 16)
                val compressedSize = bytes.u32(cursor + 20)
                val expandedSize = bytes.u32(cursor + 24)
                val nameLength = bytes.u16(cursor + 28)
                val extraLength = bytes.u16(cursor + 30)
                val entryCommentLength = bytes.u16(cursor + 32)
                val diskStart = bytes.u16(cursor + 34)
                val externalAttributes = bytes.u32(cursor + 38)
                val localOffset = bytes.u32(cursor + 42)
                val fullLength = 46L + nameLength + extraLength + entryCommentLength
                bytes.requireRange(cursor, fullLength, eocd)
                if (diskStart != 0 || compressedSize == 0xffff_ffffL || expandedSize == 0xffff_ffffL ||
                    localOffset == 0xffff_ffffL
                ) {
                    return rejected("PROJECT_ARCHIVE_ZIP64_OR_MULTIDISK_UNSUPPORTED")
                }
                if (flags and 0x0001 != 0 || flags and 0x0040 != 0 || flags and 0x2000 != 0) {
                    return rejected("PROJECT_ARCHIVE_ENCRYPTED")
                }
                if (flags and ALLOWED_FLAGS.inv() != 0) return rejected("PROJECT_ARCHIVE_FLAGS_UNSUPPORTED")
                if (method != 0 && method != 8) return rejected("PROJECT_ARCHIVE_COMPRESSION_UNSUPPORTED")
                if (nameLength == 0) return rejected("UNSAFE_PROJECT_ARCHIVE_ENTRY")

                val nameStart = cursor + 46
                val nameBytes = bytes.copyOfRange(nameStart, nameStart + nameLength)
                if (nameBytes.any { (it.toInt() and 0xff) !in 0x20..0x7e }) {
                    return rejected("UNSAFE_PROJECT_ARCHIVE_ENTRY")
                }
                val name = nameBytes.decodeToString()
                val isDirectory = name.endsWith('/')
                if (isDirectory && !allowDirectories || !isLexicallySafePath(name, allowDirectories)) {
                    return rejected("UNSAFE_PROJECT_ARCHIVE_ENTRY")
                }
                if (!seenNames.add(name.lowercase())) return rejected("PROJECT_ARCHIVE_DUPLICATE_ENTRY")
                val limit = if (isDirectory) 1 else entryLimit(name)
                if (limit <= 0) return rejected("PROJECT_ARCHIVE_ENTRY_NOT_ALLOWED")
                if (isDirectory && (expandedSize != 0L || compressedSize != 0L)) {
                    return rejected("PROJECT_ARCHIVE_DIRECTORY_INVALID")
                }
                if (!isSafeExtraFields(bytes, nameStart + nameLength, extraLength)) {
                    return rejected("PROJECT_ARCHIVE_EXTRA_FIELD_UNSUPPORTED")
                }
                if (expandedSize > limit || expandedSize > maxExpandedBytes) {
                    return rejected("PROJECT_ARCHIVE_ENTRY_TOO_LARGE")
                }
                if (expandedSize > 0 && (compressedSize == 0L || expandedSize > compressedSize * maxCompressionRatio)) {
                    return rejected("PROJECT_ARCHIVE_COMPRESSION_RATIO_EXCEEDED")
                }
                expandedTotal += expandedSize
                if (expandedTotal > maxExpandedBytes) return rejected("PROJECT_ARCHIVE_EXPANSION_LIMIT")

                val operatingSystem = versionMadeBy ushr 8
                val mode = ((externalAttributes ushr 16) and 0xffff).toInt()
                val fileType = mode and 0xf000
                if ((!isDirectory && externalAttributes and 0x10L != 0L) ||
                    (operatingSystem == 3 || operatingSystem == 19) &&
                    (fileType == 0xa000 ||
                        fileType != 0 && fileType != 0x8000 && !(isDirectory && fileType == 0x4000) ||
                        !isDirectory && mode and 0x49 != 0)
                ) {
                    return rejected("PROJECT_ARCHIVE_FILE_TYPE_UNSUPPORTED")
                }

                val entry = StudentProjectZipEntry(name, method, flags, crc, compressedSize, expandedSize, isDirectory)
                entries[name] = entry

                val localRangeEnd = validateLocalHeader(
                    bytes = bytes,
                    localOffset = localOffset,
                    centralOffset = directoryOffset,
                    name = name,
                    nameBytes = nameBytes,
                    entry = entry,
                ) ?: return rejected("PROJECT_ARCHIVE_LOCAL_HEADER_MISMATCH")
                dataRanges += localOffset until localRangeEnd
                cursor += fullLength.toInt()
            }
            if (cursor.toLong() != directoryOffset + directorySize) {
                return rejected("PROJECT_ARCHIVE_DIRECTORY_INVALID")
            }
            val sortedRanges = dataRanges.sortedBy { it.first }
            if (sortedRanges.isEmpty() || sortedRanges.first().first != 0L ||
                sortedRanges.zipWithNext().any { (left, right) -> left.last + 1 != right.first } ||
                sortedRanges.last().last + 1 != directoryOffset
            ) {
                return rejected("PROJECT_ARCHIVE_ENTRY_LAYOUT_INVALID")
            }
            return StudentProjectZipPreflightResult.Valid(entries)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return rejected("PROJECT_ARCHIVE_INVALID_ZIP")
        }
    }

    fun crc32(bytes: ByteArray): Long {
        var crc = 0xffff_ffffL
        bytes.forEach { byte ->
            crc = crc xor (byte.toLong() and 0xff)
            repeat(8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xedb8_8320L else crc ushr 1
            }
        }
        return crc xor 0xffff_ffffL
    }

    private fun validateLocalHeader(
        bytes: ByteArray,
        localOffset: Long,
        centralOffset: Long,
        name: String,
        nameBytes: ByteArray,
        entry: StudentProjectZipEntry,
    ): Long? {
        if (localOffset > Int.MAX_VALUE) return null
        val offset = localOffset.toInt()
        bytes.requireRange(offset, 30, centralOffset.toInt())
        if (bytes.u32(offset) != LOCAL_SIGNATURE) return null
        val flags = bytes.u16(offset + 6)
        val method = bytes.u16(offset + 8)
        val localCrc = bytes.u32(offset + 14)
        val localCompressedSize = bytes.u32(offset + 18)
        val localExpandedSize = bytes.u32(offset + 22)
        val localNameLength = bytes.u16(offset + 26)
        val localExtraLength = bytes.u16(offset + 28)
        if (flags != entry.flags || method != entry.method || localNameLength != nameBytes.size) return null
        val localNameStart = offset + 30
        bytes.requireRange(localNameStart, localNameLength.toLong() + localExtraLength, centralOffset.toInt())
        val localName = bytes.copyOfRange(localNameStart, localNameStart + localNameLength)
        if (!localName.contentEquals(nameBytes) || localName.decodeToString() != name ||
            !isSafeExtraFields(bytes, localNameStart + localNameLength, localExtraLength)
        ) return null
        val hasDataDescriptor = flags and 0x0008 != 0
        if (hasDataDescriptor) {
            if (localCrc != 0L && localCrc != entry.crc32 ||
                localCompressedSize != 0L && localCompressedSize != entry.compressedSize ||
                localExpandedSize != 0L && localExpandedSize != entry.expandedSize
            ) return null
        } else if (localCrc != entry.crc32 || localCompressedSize != entry.compressedSize ||
            localExpandedSize != entry.expandedSize
        ) return null

        val dataStart = localNameStart.toLong() + localNameLength + localExtraLength
        val dataEnd = dataStart + entry.compressedSize
        if (dataEnd > centralOffset) return null
        if (!hasDataDescriptor) return dataEnd
        return descriptorEnd(bytes, dataEnd, centralOffset, entry)
    }

    private fun descriptorEnd(
        bytes: ByteArray,
        offset: Long,
        centralOffset: Long,
        entry: StudentProjectZipEntry,
    ): Long? {
        fun matches(start: Long): Boolean {
            if (start + 12 > centralOffset) return false
            val index = start.toInt()
            return bytes.u32(index) == entry.crc32 &&
                bytes.u32(index + 4) == entry.compressedSize &&
                bytes.u32(index + 8) == entry.expandedSize
        }
        if (offset + 16 <= centralOffset && bytes.u32(offset.toInt()) == DATA_DESCRIPTOR_SIGNATURE && matches(offset + 4)) {
            return offset + 16
        }
        if (matches(offset)) return offset + 12
        return null
    }

    private fun findEocd(bytes: ByteArray): Int? {
        val first = (bytes.size - 22 - MAX_COMMENT_LENGTH).coerceAtLeast(0)
        for (offset in bytes.size - 22 downTo first) {
            if (bytes.u32(offset) == EOCD_SIGNATURE) {
                val commentLength = bytes.u16(offset + 20)
                if (offset + 22 + commentLength == bytes.size) return offset
            }
        }
        return null
    }

    private fun isSafeExtraFields(bytes: ByteArray, offset: Int, size: Int): Boolean {
        var cursor = offset
        val end = offset + size
        if (end > bytes.size) return false
        while (cursor < end) {
            if (cursor + 4 > end) return false
            val id = bytes.u16(cursor)
            val length = bytes.u16(cursor + 2)
            if (cursor + 4 + length > end || id == 0x0001 || id == 0x0017 || id == 0x0018 ||
                id == 0x9901 || id == 0x7075
            ) return false
            cursor += 4 + length
        }
        return cursor == end
    }

    private fun isLexicallySafePath(name: String, allowDirectories: Boolean): Boolean {
        val path = if (allowDirectories && name.endsWith('/')) name.dropLast(1) else name
        return path.isNotEmpty() && path.length <= 120 && path.all { it.code in 0x20..0x7e } &&
            '\\' !in path && ':' !in path && path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun rejected(code: String) = StudentProjectZipPreflightResult.Rejected(code)

    private fun ByteArray.u16(offset: Int): Int {
        requireRange(offset, 2, size)
        return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.u32(offset: Int): Long {
        requireRange(offset, 4, size)
        return (this[offset].toLong() and 0xff) or
            ((this[offset + 1].toLong() and 0xff) shl 8) or
            ((this[offset + 2].toLong() and 0xff) shl 16) or
            ((this[offset + 3].toLong() and 0xff) shl 24)
    }

    private fun ByteArray.requireRange(offset: Int, length: Long, endExclusive: Int) {
        require(offset >= 0 && length >= 0 && offset.toLong() + length <= endExclusive.toLong())
    }

    private fun ByteArray.requireRange(offset: Int, length: Int, endExclusive: Int) =
        requireRange(offset, length.toLong(), endExclusive)

}
