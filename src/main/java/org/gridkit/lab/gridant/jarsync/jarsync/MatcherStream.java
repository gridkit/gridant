/* MatcherStream.java -- streaming alternative to Matcher.
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

import java.util.LinkedList;
import java.util.List;

/**
 * A streaming version of {@link Matcher}. The idea here is that the hashtable
 * search is the most expensive operation in the rsync algorithm, and it is at
 * times undesirable to wait for this to finish while whoever on the other end
 * of the wire is waiting for our data. With this construction, we can send
 * {@link Delta}s as soon as they are generated.
 * 
 * <p>
 * To implement the outgoing stream of Deltas, this class makes use of a
 * callback interface, {@link MatcherListener}. Pass a concrete implementation
 * of this interface to the {@link #addListener(MatcherListener)} method, and a
 * {@link java.util.List} of {@link ChecksumPair}s before calling any of the
 * <code>update</code> methods. Once the data have been passed to these methods,
 * call {@link #doFinal()} to finish the process.
 */
public class MatcherStream {

    /**
     * The configuration.
     */
    protected final Configuration config;

    /**
     * The list of {@link MatcherListener}s.
     */
    protected final List<MatcherListener> listeners;

    /**
     * The current hashtable.
     */
    protected final TwoKeyMap<Long> hashtable;

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

    /**
     * Create a new MatcherStream.
     * 
     * @param config
     *            The current configuration.
     */
    public MatcherStream(Configuration config) {
        this.config = config;
        this.listeners = new LinkedList<MatcherListener>();
        this.hashtable = new TwoKeyMap<Long>();
        buffer = new byte[config.chunkSize];
        reset();
    }

    /**
     * Add a {@link MatcherListener} to the list of listeners.
     * 
     * @param listener
     *            The listener to add.
     */
    public void addListener(MatcherListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a {@link MatcherListener} from the list of listeners.
     * 
     * @param listener
     *            The listener to add.
     * @return True if a listener was really removed (i.e. that the listener was
     *         in the list to begin with).
     */
    public boolean removeListener(MatcherListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Set the list of checksums that will be searched by this matcher. This
     * method must be called at least once before calling update.
     * 
     * @param sums
     *            The checksums.
     */
    public void setChecksums(List<ChecksumPair> sums) {
        hashtable.clear();
        if (sums != null) {
            for (ChecksumPair p : sums)
                hashtable.put(p, p.getOffset());
        }

        if (config.debug) {
            System.out.printf("[MATCHER] setChecksums hashtable: %s%n", hashtable);
        }
    }

    /**
     * Reset this matcher, to be used for another data set.
     */
    public void reset() {
        ndx = 0;
        count = 0L;
        hashtable.clear();
    }

    /**
     * Update this matcher with a single byte.
     * 
     * @param b
     *            The next byte
     */
    public void update(byte b) throws ListenerException {
        if (config.debug) {
            System.out.printf("[MATCHER] update %x%n", b & 0xFF);
        }
        ListenerException exception = null, current = null;
        buffer[ndx++] = b;
        count++;
        if (ndx < config.blockLength) {
            // We have not seen a full block since the last match.
            return;
        } else if (ndx == config.blockLength) {
            config.weakSum.check(buffer, 0, config.blockLength);
        } else {
            config.weakSum.roll(b);
        }
        Long oldOffset = hashSearch(buffer, ndx - config.blockLength, config.blockLength);
        if (oldOffset != null) {
            if (ndx > config.blockLength) {
                DataBlock d = new DataBlock(count - ndx, buffer, 0, ndx - config.blockLength);
                Offsets o = new Offsets(oldOffset.longValue(), count - config.blockLength, config.blockLength);
                for (MatcherListener l : listeners) {
                    try {
                        l.update(new MatcherEvent(d));
                        l.update(new MatcherEvent(o));
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
            } else {
                Offsets o = new Offsets(oldOffset.longValue(), count - config.blockLength, config.blockLength);
                for (MatcherListener l : listeners) {
                    try {
                        l.update(new MatcherEvent(o));
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
            ndx = 0;
        } else if (ndx == buffer.length) {
            DataBlock d = new DataBlock(count - ndx, buffer, 0, buffer.length - (config.blockLength - 1));
            for (MatcherListener l : listeners) {
                try {
                    l.update(new MatcherEvent(d));
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
            System.arraycopy(buffer, buffer.length - (config.blockLength - 1), buffer, 0, config.blockLength - 1);
            ndx = config.blockLength - 1;
        }
    }

    /**
     * Update this matcher with a portion of a byte array.
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
            System.out.printf("[MATCHER] update %s %d %d%n", buf, off, len);
        }
        ListenerException exception = null, current = null;
        Long oldOffset;
        int i = off;

        while (i < len + off) {
            byte bt = buffer[ndx++] = buf[i++];
            count++;
            if (ndx < config.blockLength) {
                continue;
            } else if (ndx == config.blockLength) {
                config.weakSum.check(buffer, 0, config.blockLength);
            } else {
                config.weakSum.roll(bt);
            }
            oldOffset = hashSearch(buffer, ndx - config.blockLength, config.blockLength);
            if (oldOffset != null) {
                if (ndx > config.blockLength) {
                    DataBlock d = new DataBlock(count - ndx, buffer, 0, ndx - config.blockLength);
                    Offsets o = new Offsets(oldOffset.longValue(), count - config.blockLength, config.blockLength);
                    for (MatcherListener l : listeners) {
                        try {
                            l.update(new MatcherEvent(d));
                            l.update(new MatcherEvent(o));
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
                } else {
                    Offsets o = new Offsets(oldOffset.longValue(), count - config.blockLength, config.blockLength);
                    for (MatcherListener l : listeners) {
                        try {
                            l.update(new MatcherEvent(o));
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
                }
                if (exception != null)
                    throw exception;
                ndx = 0;
            } else if (ndx == buffer.length) {
                DataBlock d = new DataBlock(count - ndx, buffer, 0, buffer.length - (config.blockLength - 1));
                for (MatcherListener l : listeners) {
                    try {
                        l.update(new MatcherEvent(d));
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
                System.arraycopy(buffer, buffer.length - (config.blockLength - 1), buffer, 0, config.blockLength - 1);
                ndx = config.blockLength - 1;
            }
        }
    }

    /**
     * Update this matcher with a byte array.
     * 
     * @param buf
     *            The next bytes.
     */
    public void update(byte[] buf) throws ListenerException {
        update(buf, 0, buf.length);
    }

    /**
     * Flush any buffered data and reset this instance.
     */
    public void doFinal() throws ListenerException {
        if (config.debug) {
            System.out.printf("[MATCHER] doFinal%n");
        }
        ListenerException exception = null, current = null;
        if (ndx > 0) {
            int off = Math.max(0, ndx - config.blockLength);
            int len = Math.min(ndx, config.blockLength);
            config.weakSum.check(buffer, off, len);
            Long oldOff = hashSearch(buffer, off, len);
            if (oldOff != null) {
                if (off > 0) {
                    DataBlock d = new DataBlock(count - ndx, buffer, 0, off);
                    for (MatcherListener l : listeners) {
                        try {
                            l.update(new MatcherEvent(d));
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
                Offsets o = new Offsets(oldOff.longValue(), count - len, len);
                for (MatcherListener l : listeners) {
                    try {
                        l.update(new MatcherEvent(o));
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
            } else {
                DataBlock d = new DataBlock(count - ndx, buffer, 0, ndx);
                for (MatcherListener l : listeners) {
                    try {
                        l.update(new MatcherEvent(d));
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
        }
        reset();
    }

    /**
     * Search if a portion of the given byte array is in the map, returning its
     * original offset if it is.
     * 
     * @param block
     *            The block of bytes to search for.
     * @param off
     *            The offset in the block to begin.
     * @param len
     *            The number of bytes to read from the block.
     * @return The original offset of the given block if it was found in the
     *         map. null if it was not found.
     */
    protected Long hashSearch(byte[] block, int off, int len) {
        Integer weakSum = new Integer(config.weakSum.getValue());
        if (hashtable.containsKey(weakSum.intValue())) {
            if (config.debug) {
                System.out.printf("[MATCHER] hashSearch weakSum 2: %x%n", weakSum);
            }
            if (hashtable.containsKey(weakSum)) {
                if (config.debug) {
                    System.out.printf("[MATCHER] hashSearch strongSum%n");
                }
                config.strongSum.reset();
                config.strongSum.update(block, off, len);
                if (config.checksumSeed != null) {
                    config.strongSum.update(config.checksumSeed);
                }
                byte[] digest = new byte[config.strongSumLength];
                System.arraycopy(config.strongSum.digest(), 0, digest, 0, digest.length);
                ChecksumPair pair = new ChecksumPair(weakSum, digest);
                if (config.debug) {
                    System.out.printf("[MATCHER] looking up %s%n", pair);
                }
                Long offset = hashtable.get(pair);
                if (config.debug) {
                    if (offset != null)
                        System.out.printf("[MATCHER] found offset %d%n", offset);
                    else
                        System.out.printf("[MATCHER] not found%n");
                }
                return offset;
            }
        }
        return null;
    }
}
