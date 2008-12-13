		assertEquals(-1, fh.parseGitFileName(0, fh.buf.length));
		final FileHeader fh = data("a/ b/");
		assertEquals(-1, fh.parseGitFileName(0, fh.buf.length));
		final FileHeader fh = data("\n");
		assertEquals(-1, fh.parseGitFileName(0, fh.buf.length));
		final FileHeader fh = data("\n\n");
		assertEquals(1, fh.parseGitFileName(0, fh.buf.length));
		assertEquals(gitLine(name).length(), fh.parseGitFileName(0,
				fh.buf.length));
		assertTrue(fh.parseGitFileName(0, fh.buf.length) > 0);
		assertEquals(gitLine(name).length(), fh.parseGitFileName(0,
				fh.buf.length));
		assertEquals(dqGitLine(dqName).length(), fh.parseGitFileName(0,
				fh.buf.length));
		assertEquals(dqGitLine(dqName).length(), fh.parseGitFileName(0,
				fh.buf.length));
		assertEquals(gitLine(name).length(), fh.parseGitFileName(0,
				fh.buf.length));
		assertEquals(header.length(), fh.parseGitFileName(0, fh.buf.length));
		assertSame(FileMode.MISSING, fh.getOldMode());
		assertSame(FileMode.MISSING, fh.getNewMode());
		int ptr = fh.parseGitFileName(0, fh.buf.length);
		ptr = fh.parseGitHeaders(ptr, fh.buf.length);
		int ptr = fh.parseGitFileName(0, fh.buf.length);
		ptr = fh.parseGitHeaders(ptr, fh.buf.length);
		int ptr = fh.parseGitFileName(0, fh.buf.length);
		ptr = fh.parseGitHeaders(ptr, fh.buf.length);
		int ptr = fh.parseGitFileName(0, fh.buf.length);
		ptr = fh.parseGitHeaders(ptr, fh.buf.length);