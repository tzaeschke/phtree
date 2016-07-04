/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
 *
 * This file is part of ZooDB.
 *
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See the README and COPYING files for further information.
 */
package ch.ethz.globis.pht.util;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Fast drop-in replacement for java.util.Scanner.
 * 
 * @author Tilmann Zaschke
 *
 */
public class FastScanner {

	private final BufferedReader reader;
	
	private String currentLine = null;
	private int currentPos = 0;
	
	public FastScanner(BufferedReader reader) {
		this.reader = reader;
		try {
			currentLine = reader.readLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String nextLine() {
		try {
			String ret = currentLine.substring(currentPos);
			currentLine = reader.readLine();
			if (currentLine != null) {
				currentLine = currentLine.trim();
			}
			currentPos = 0;
			return ret;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void close() {
		try {
			reader.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean hasNext() {
		return currentLine != null;
	}

	public String next() {
		int pos;
		while ((pos = currentLine.indexOf(' ', currentPos)) == currentPos) {
			currentPos++;
		}
		
		String ret;
		if (pos < 0) {
			ret = currentLine.substring(currentPos);
			nextLine();
		} else {
			ret = currentLine.substring(currentPos, pos);
			currentPos = pos + 1;
		}
		return ret;
	}

}
