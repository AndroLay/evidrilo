using System.Text;
using Evidrilo.Api.Billing;

namespace Evidrilo.Api.Tests;

public sealed class BillingRequestBodyTests
{
    [Fact]
    public async Task Chunked_body_reader_stops_at_the_first_byte_over_the_route_limit()
    {
        const int limit = 128 * 1024;
        await using var body = new ChunkedReadStream(limit + 1024 * 1024);

        var result = await BillingRequestBodyReader.ReadAsync(body, limit, CancellationToken.None);

        Assert.Null(result);
        Assert.Equal(limit + 1, body.BytesRead);
    }

    [Fact]
    public async Task Body_reader_preserves_a_body_at_the_route_limit()
    {
        const int limit = 128 * 1024;
        var expected = Encoding.UTF8.GetBytes(new string('a', limit));
        await using var body = new MemoryStream(expected, writable: false);

        var result = await BillingRequestBodyReader.ReadAsync(body, limit, CancellationToken.None);

        Assert.NotNull(result);
        Assert.Equal(expected, result);
    }

    private sealed class ChunkedReadStream : Stream
    {
        private readonly long length;
        private long position;

        public ChunkedReadStream(long length)
        {
            this.length = length;
        }

        public long BytesRead => position;

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => length;
        public override long Position
        {
            get => position;
            set => throw new NotSupportedException();
        }

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();

        public override ValueTask<int> ReadAsync(
            Memory<byte> buffer,
            CancellationToken cancellationToken = default)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (position >= length) return ValueTask.FromResult(0);
            var read = (int)Math.Min(buffer.Length, length - position);
            buffer.Span[..read].Fill((byte)'a');
            position += read;
            return ValueTask.FromResult(read);
        }

        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override void Flush() => throw new NotSupportedException();
    }
}
