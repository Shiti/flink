/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.flink.core.fs.local;

import org.apache.flink.core.fs.FSDataInputStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * The <code>LocalDataInputStream</code> class is a wrapper class for a data
 * input stream to the local file system.
 * 
 */
public class LocalMappedDataInputStream extends FSDataInputStream {

	/**
	 * The file input stream used to read data.
	 */
	private final String MMAP_SIZE_PROP = "org.flink.fs.mmap.size";

	private FileChannel fc = null;
	private long bufferSize = System.getProperty(MMAP_SIZE_PROP) == null ?
					1024 * 1024 * 100 : Long.parseLong(System.getProperty(MMAP_SIZE_PROP));

	private long fileSize = 0;

	private long currentStartAt = 0;
	private long currentEndAt  = 0;
	private MappedByteBuffer mbb = null;



	/**
	 * Constructs a new <code>LocalDataInputStream</code> object from a given {@link File} object.
	 *
	 * @param file
	 *        the {@link File} object the data stream is written to
	 * @throws IOException
	 *         thrown if the data input stream cannot be created
	 */
	public LocalMappedDataInputStream(final File file) throws IOException {

		//this.fis = new FileInputStream(file);
		this.fileSize = file.length();
		this.fc = new RandomAccessFile(file, "r").getChannel();
		long readSize = this.fileSize < this.bufferSize ? this.fileSize : this.bufferSize;
		readBuffer(0, readSize);

	}

	private void readBuffer(long from, long size) throws IOException {
		this.mbb = this.fc.map(FileChannel.MapMode.READ_ONLY, from, size);
		this.currentStartAt = from;
		this.currentEndAt = from + size;
	}


	@Override
	public void seek(final long desired) throws IOException {
		if(this.currentStartAt > desired && this.currentEndAt < desired) {
			long afterDesired = this.fileSize - desired;
			long readSize =  afterDesired < this.bufferSize ? afterDesired : this.bufferSize;
			readBuffer(desired, readSize);
		} else {
			this.mbb.position((int)(desired - currentStartAt));
		}
	}

	@Override
	public long getPos() throws IOException {
		return this.currentStartAt + this.mbb.position();
	}


	@Override
	public int read() throws IOException {
		return this.mbb.getInt();
	}


	@Override
	public int read(final byte[] buffer, final int offset, final int length) throws IOException {
		long cpos = getPos();
		long remaining = this.fileSize  - cpos;

		if(remaining == 0) {
			//Do nothing and just return -1 EOF
			return -1;
		}

		int respSize = (int) (remaining > length ? length : remaining);

		if(this.mbb.remaining() < respSize){
			readBuffer(cpos, respSize > this.bufferSize ? respSize : this.bufferSize);
		}

		mbb.get(buffer, offset, respSize);
		return respSize;
	}


	@Override
	public void close() throws IOException {
		this.fc.close();
	}


	@Override
	public int available() throws IOException {
		//Note: This is not the remaining number of bytes, but bytes that can be read without blocking.
		return (int) (this.fileSize - getPos());
	}


	@Override
	public long skip(final long n) throws IOException {
		long rem = fileSize - this.getPos();
		long actualSkip = rem < n ? rem : n;
		this.seek(getPos() + actualSkip);
		return actualSkip;
	}

}
