/* GeneratorStream.java -- streaming alternative to Generator.
   Copyright (C) 2003, 2007  Casey Marshall <rsdio@metastatic.org>

This file is a part of Jarsync.

Jarsync is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation; either version 2 of the License, or (at your
option) any later version.

Jarsync is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License
along with Jarsync; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

Linking Jarsync statically or dynamically with other modules is making
a combined work based on Jarsync.  Thus, the terms and conditions of
the GNU General Public License cover the whole combination.

As a special exception, the copyright holders of Jarsync give you
permission to link Jarsync with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on Jarsync.  If you modify Jarsync, you may extend this
exception to your version of it, but you are not obligated to do so.
If you do not wish to do so, delete this exception statement from your
version.

ALTERNATIVELY, Jarsync may be licensed under the Apache License,
Version 2.0 (the "License"); you may not use this file except in
compliance with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied.  See the License for the specific language governing
permissions and limitations under the License.

If you modify Jarsync, you may extend this same choice of license for
your library, but you are not obligated to do so. If you do not offer
the same license terms, delete the license terms that your library is
NOT licensed under.  */

package org.gridkit.lab.gridant.jarsync.jarsync;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class GeneratorStream {

    /**
     * The configuration.
     */
    protected final Configuration config;

    /**
     * The list of {@link GeneratorListeners}.
     */
    protected final List<GeneratorListener> listeners;

    /**
     * The intermediate byte buffer.
     */
    protected final byte[] buffer;

    /**
     * The current index in {@link #buffer}.
     */
    protected int ndx;

    /**
     * The number of bytes summed thusfar.
     */
    protected long count;

    public GeneratorStream(Configuration config) {
        this.config = config;
        this.listeners = new LinkedList<GeneratorListener>();
        buffer = new byte[config.blockLength];
        reset();
    }

    /**
     * Add a {@link GeneratorListener} to the list of listeners.
     * 
     * @param listener
     *            The listener to add.
     */
    public void addListener(GeneratorListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        listeners.add(listener);
    }

    /**
     * Remove a {@link GeneratorListener} from the list of listeners.
     * 
     * @param listener
     *            The listener to add.
     * @return True if a listener was really removed (i.e. that the listener was
     *         in the list to begin with).
     */
    public boolean removeListener(GeneratorListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Reset this generator, to be used for another data set.
     */
    public void reset() {
        ndx = 0;
        count = 0L;
    }

    /**
     * Update this generator with a single byte.
     * 
     * @param b
     *            The next byte
     */
    public void update(byte b) throws ListenerException {
        if (config.debug) {
            System.out.printf("[GENERATOR] update %b", b & 0xff);
        }
        ListenerException exception = null, current = null;
        buffer[ndx++] = b;
        if (ndx == buffer.length) {
            ChecksumPair p = generateSum(buffer, 0, buffer.length);
            for (GeneratorListener listener : listeners) {
                try {
                    listener.update(new GeneratorEvent(p));
                } catch (ListenerException le) {
                    if (exception != null) {
                        current.setNext(le);
                        current = le;
                    } else {
                        exception = le;
                        current = le;
                    }
                }
            }
            if (exception != null)
                throw exception;
            ndx = 0;
        }
    }

    /**
     * Update this generator with a portion of a byte array.
     * 
     * @param buf
     *            The next bytes.
     * @param off
     *            The offset to begin at.
     * @param len
     *            The number of bytes to update.
     */
    public void update(byte[] buf, int off, int len) throws ListenerException {
        if (config.debug) {
            System.out.printf("[GENERATOR] update %s %d %d%n", buf, off, len);
        }
        ListenerException exception = null, current = null;
        int i = off;
        do {
            int l = Math.min(len - (i - off), buffer.length - ndx);
            System.arraycopy(buf, i, buffer, ndx, l);
            i += l;
            ndx += l;
            if (ndx == buffer.length) {
                ChecksumPair p = generateSum(buffer, 0, buffer.length);
                for (Iterator<GeneratorListener> it = listeners.listIterator(); it.hasNext();) {
                    try {
                        ((GeneratorListener) it.next()).update(new GeneratorEvent(p));
                    } catch (ListenerException le) {
                        if (exception != null) {
                            current.setNext(le);
                            current = le;
                        } else {
                            exception = le;
                            current = le;
                        }
                    }
                }
                if (exception != null)
                    throw exception;
                ndx = 0;
            }
        } while (i < off + len);
    }

    /**
     * Update this generator with a byte array.
     * 
     * @param buf
     *            The next bytes.
     */
    public void update(byte[] buf) throws ListenerException {
        update(buf, 0, buf.length);
    }

    /**
     * Finish generating checksums, flushing any buffered data and resetting
     * this instance.
     */
    public void doFinal() throws ListenerException {
        if (config.debug) {
            System.out.printf("[GENERATOR] doFinal%n");
        }
        ListenerException exception = null, current = null;
        if (ndx > 0) {
            ChecksumPair p = generateSum(buffer, 0, ndx);
            for (GeneratorListener listener : listeners) {
                try {
                    listener.update(new GeneratorEvent(p));
                } catch (ListenerException le) {
                    if (exception != null) {
                        current.setNext(le);
                        current = le;
                    } else {
                        exception = le;
                        current = le;
                    }
                }
            }
            if (exception != null)
                throw exception;
        }
        reset();
    }

    // Own methods.
    // -----------------------------------------------------------------------

    /**
     * Generate a sum pair for a portion of a byte array.
     * 
     * @param buf
     *            The byte array to checksum.
     * @param off
     *            Where in <code>buf</code> to start.
     * @param len
     *            How many bytes to checksum.
     * @return A {@link ChecksumPair} for this byte array.
     */
    protected ChecksumPair generateSum(byte[] buf, int off, int len) {
        ChecksumPair p = new ChecksumPair();
        config.weakSum.check(buf, off, len);
        config.strongSum.update(buf, off, len);
        if (config.checksumSeed != null) {
            config.strongSum.update(config.checksumSeed, 0, config.checksumSeed.length);
        }
        p.weak = config.weakSum.getValue();
        p.strong = new byte[config.strongSumLength];
        System.arraycopy(config.strongSum.digest(), 0, p.strong, 0, config.strongSumLength);
        p.offset = count;
        p.length = len;
        count += len;
        if (config.debug) {
            System.out.printf("[GENERATOR] generated sum %s%n", p);
        }
        return p;
    }
}
