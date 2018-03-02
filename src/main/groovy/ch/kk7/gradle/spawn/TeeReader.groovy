package ch.kk7.gradle.spawn

/**
 * primitive duplicating stream reader.
 */
class TeeReader extends FilterReader {

	private final Writer tee

	TeeReader(Reader reader, Writer tee) {
		super(reader)
		this.tee = tee
	}

	@Override
	int read() throws IOException {
		int read = in.read()
		if (read != -1) {
			tee.write(read)
			tee.flush()
		}
		return read
	}

	@Override
	int read(char[] cbuf) throws IOException {
		int read = in.read(cbuf)
		if (read != -1) {
			tee.write(cbuf, 0, read)
			tee.flush()
		}
		return read
	}

	@Override
	int read(char[] cbuf, int off, int len) throws IOException {
		int read = in.read(cbuf, off, len)
		if (read != -1) {
			tee.write(cbuf, off, read)
			tee.flush()
		}
		return read
	}

}
