package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.practice.*
import kotlinx.serialization.json.*

interface PracticeCourseStore {
    fun load(): LocalStorageReadResult<PracticeCourseState>
    fun save(state: PracticeCourseState): LocalStorageWriteResult
}

/** Separate record: never reads/writes tablet session, case history, chat or Projects. */
class EncodedPracticeCourseStore(
    private val read: () -> String?,
    private val write: (String) -> Boolean,
) : PracticeCourseStore {
    override fun load(): LocalStorageReadResult<PracticeCourseState> = runCatching {
        val raw = read() ?: return LocalStorageReadResult.Success(null)
        val decoded = PracticeCourseCodec.decode(raw) ?: return LocalStorageReadResult.Corrupt
        LocalStorageReadResult.Success(decoded)
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(state: PracticeCourseState): LocalStorageWriteResult = runCatching {
        val text = PracticeCourseCodec.encode(state)
        if (PracticeCourseCodec.decode(text) == null) return LocalStorageWriteResult.FAILED
        if (write(text)) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}

class InMemoryPracticeCourseStore : PracticeCourseStore {
    private var encoded: String? = null
    private val delegate = EncodedPracticeCourseStore(
        read = { encoded },
        write = { encoded = it; true },
    )
    override fun load(): LocalStorageReadResult<PracticeCourseState> = delegate.load()
    override fun save(state: PracticeCourseState): LocalStorageWriteResult = delegate.save(state)
}

class UnavailablePracticeCourseStore : PracticeCourseStore {
    override fun load(): LocalStorageReadResult<PracticeCourseState> = LocalStorageReadResult.Unavailable
    override fun save(state: PracticeCourseState): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}

internal object PracticeCourseCodec {
    fun encode(state: PracticeCourseState): String = buildJsonObject {
        put("version", PracticeCourseContent.VERSION)
        put("active", state.active?.name?.let(::JsonPrimitive) ?: JsonNull)
        put("sessions", buildJsonArray {
            state.sessions.entries.sortedBy { it.key.ordinal }.forEach { (_, session) ->
                add(buildJsonObject {
                    put("lesson", session.lesson.name)
                    put("stage", session.stage.name)
                    put("prediction", session.prediction?.let(::JsonPrimitive) ?: JsonNull)
                    put("draft", draft(session.draft))
                    put("initial", session.initial?.let(::draft) ?: JsonNull)
                    put("revised", session.revised?.let(::draft) ?: JsonNull)
                    put("changeChoice", session.changeChoice?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("changeReviewed", session.changeReviewed)
                })
            }
        })
    }.toString()

    fun decode(raw: String): PracticeCourseState? = runCatching {
        require(raw.length <= 32_768)
        val root = Json.parseToJsonElement(raw).jsonObject
        require(root["version"]?.jsonPrimitive?.content == PracticeCourseContent.VERSION)
        val active = nullableEnum<PracticeLessonId>(root["active"])
        val sessions = root.getValue("sessions").jsonArray.map { value ->
            val obj = value.jsonObject
            val lesson = requiredEnum<PracticeLessonId>(obj.getValue("lesson"))
            val session = PracticeLessonSession(
                lesson = lesson,
                stage = requiredEnum(obj.getValue("stage")),
                prediction = nullableText(obj.getValue("prediction")),
                draft = readDraft(obj.getValue("draft").jsonObject),
                initial = obj.getValue("initial").takeUnless { it == JsonNull }?.jsonObject?.let(::readDraft),
                revised = obj.getValue("revised").takeUnless { it == JsonNull }?.jsonObject?.let(::readDraft),
                changeChoice = nullableEnum(obj.getValue("changeChoice")),
                changeReviewed = obj.getValue("changeReviewed").jsonPrimitive.boolean,
            )
            require(valid(session))
            session
        }
        require(sessions.size <= 3 && sessions.map { it.lesson }.distinct().size == sessions.size)
        require(active == null || sessions.any { it.lesson == active })
        PracticeCourseState(active, sessions.associateBy { it.lesson })
    }.getOrNull()

    private fun draft(value: PracticeLessonDraft): JsonObject = buildJsonObject {
        put("evidence", buildJsonArray { value.evidence.sorted().forEach { add(JsonPrimitive(it)) } })
        put("groups", buildJsonObject { value.groups.toSortedMap().forEach { (id, group) -> put(id, group.name) } })
        put("claim", value.claim)
        put("scope", value.scope?.name?.let(::JsonPrimitive) ?: JsonNull)
        put("limitation", value.limitation?.name?.let(::JsonPrimitive) ?: JsonNull)
        put("limitationNote", value.limitationNote)
        put("action", value.action?.name?.let(::JsonPrimitive) ?: JsonNull)
        put("actionReason", value.actionReason)
        put("evidenceVersion", value.evidenceVersion)
    }

    private fun readDraft(obj: JsonObject): PracticeLessonDraft {
        val evidence = obj.getValue("evidence").jsonArray.map { it.jsonPrimitive.content }
        require(evidence.distinct().size == evidence.size && evidence.size <= 4)
        return PracticeLessonDraft(
            evidence = evidence.toSet(),
            groups = obj.getValue("groups").jsonObject.mapValues { requiredEnum<PracticeEvidenceGroup>(it.value) },
            claim = obj.getValue("claim").jsonPrimitive.content,
            scope = nullableEnum(obj.getValue("scope")),
            limitation = nullableEnum(obj.getValue("limitation")),
            limitationNote = obj.getValue("limitationNote").jsonPrimitive.content,
            action = nullableEnum(obj.getValue("action")),
            actionReason = obj.getValue("actionReason").jsonPrimitive.content,
            evidenceVersion = obj.getValue("evidenceVersion").jsonPrimitive.int,
        ).also {
            require(it.claim.length <= 320 && it.limitationNote.length <= 240 && it.actionReason.length <= 240 &&
                it.groups.size <= 3 && it.evidenceVersion in 1..2)
        }
    }

    private fun valid(session: PracticeLessonSession): Boolean {
        if (session.prediction != null && session.prediction !in PracticeCourseContent.predictions(session.lesson)) return false
        if (session.lesson == PracticeLessonId.TABLET) return session.initial == null && session.revised == null &&
            session.stage in setOf(PracticeLessonStage.MISSION, PracticeLessonStage.PREDICTION, PracticeLessonStage.INSPECT, PracticeLessonStage.COMPLETE)
        val needsInitial = session.stage in setOf(PracticeLessonStage.FEEDBACK, PracticeLessonStage.CHANGE,
            PracticeLessonStage.REVISION, PracticeLessonStage.FINAL_REVIEW, PracticeLessonStage.COMPLETE)
        if (needsInitial != (session.initial != null)) return false
        val needsRevision = session.stage == PracticeLessonStage.FINAL_REVIEW || session.stage == PracticeLessonStage.COMPLETE
        if (needsRevision != (session.revised != null)) return false
        if (session.initial?.evidenceVersion?.let { it != 1 } == true || session.revised?.evidenceVersion?.let { it != 2 } == true) return false
        if (session.stage !in setOf(PracticeLessonStage.REVISION, PracticeLessonStage.FINAL_REVIEW, PracticeLessonStage.COMPLETE) &&
            session.draft.evidenceVersion != 1) return false
        if (session.stage in setOf(PracticeLessonStage.FEEDBACK, PracticeLessonStage.CHANGE) && session.draft != session.initial) return false
        if (needsRevision && session.draft != session.revised) return false
        if (session.stage in setOf(PracticeLessonStage.REVISION, PracticeLessonStage.FINAL_REVIEW, PracticeLessonStage.COMPLETE) &&
            (!session.changeReviewed || !PracticeCourseContent.isSupportedChange(session.lesson, session.changeChoice))) return false
        if (session.changeChoice != null && session.changeChoice !in PracticeCourseContent.changeChoices(session.lesson)) return false
        val drafts = listOfNotNull(session.draft, session.initial, session.revised)
        return drafts.all { draft ->
            draft.evidence.all { it in PracticeCourseContent.evidenceIds(session.lesson, draft.evidenceVersion == 2) } &&
                draft.groups.keys.all { session.lesson == PracticeLessonId.STUDIES && it in setOf("STUDY-A", "STUDY-B", "STUDY-C") } &&
                (draft.limitation == null || draft.limitation in PracticeCourseContent.limitations(session.lesson)) &&
                (session.lesson != PracticeLessonId.SURVEY || draft.action != PracticeNextAction.SAME_MEASURE)
        } && listOfNotNull(session.initial, session.revised).all { draft ->
            PracticeCourseReducer.inputProblem(session.copy(stage = PracticeLessonStage.ACTION, draft = draft)) == null
        } && (session.revised?.let { revised ->
            PracticeCourseReducer.revisionEvidenceProblem(
                session.copy(stage = PracticeLessonStage.REVISION, draft = revised)
            ) == null
        } != false)
    }

    private fun nullableText(element: JsonElement): String? = if (element == JsonNull) null else element.jsonPrimitive.content
    private inline fun <reified T : Enum<T>> nullableEnum(element: JsonElement?): T? =
        if (element == null || element == JsonNull) null else requiredEnum<T>(element)
    private inline fun <reified T : Enum<T>> requiredEnum(element: JsonElement): T =
        enumValues<T>().first { it.name == element.jsonPrimitive.content }
}
