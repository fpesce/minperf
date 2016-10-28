package org.minperf;

import org.minperf.universal.UniversalHash;

/**
 * Evaluate the minimum perfect hash function.
 * Concurrent reads are supported.
 *
 * @param <T> the key type
 */
public class RecSplitEvaluator<T> {

    protected final Settings settings;
    protected final UniversalHash<T> hash;
    protected final BitBuffer buffer;
    protected final long size;
    protected final int bucketCount;

    private long dataBits;
    private int tableStart;
    private int bitsPerEntry;
    private int bitsPerEntry2;
    private int headerBits;

    public RecSplitEvaluator(BitBuffer buffer, UniversalHash<T> hash,
            Settings settings) {
        this.settings = settings;
        this.hash = hash;
        this.buffer = buffer;
        this.size = (int) (buffer.readEliasDelta() - 1);
        this.bucketCount = (int) (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
    }

    public void init() {
        if (bucketCount == 1) {
            bitsPerEntry = 0;
            bitsPerEntry2 = 0;
            dataBits = 0;
        } else {
            dataBits = settings.getEstimatedBits(size) +
                    BitBuffer.unfoldSigned(buffer.readEliasDelta() - 1);
            bitsPerEntry = (int) buffer.readGolombRice(2);
            if (Settings.COMPLEX_BUCKET_HEADER) {
                bitsPerEntry2 = (int) buffer.readGolombRice(2);
            } else {
                bitsPerEntry2 = bitsPerEntry;
            }
        }
        tableStart = buffer.position();
        int tableBits = (bitsPerEntry + bitsPerEntry2) * (bucketCount - 1);
        headerBits = tableStart + tableBits;
    }

    public int evaluate(T obj) {
        long hashCode;
        int bucket;
        hashCode = hash.universalHash(obj, 0);
        if (bucketCount == 1) {
            bucket = 0;
        } else {
            bucket = Settings.scaleLong(hashCode, bucketCount);
        }
        int add, start;
        int pos;
        if (bucket == 0) {
            pos = tableStart;
            add = 0;
            start = 0;
        } else {
            pos = tableStart + (bitsPerEntry + bitsPerEntry2) * (bucket - 1);
            int expectedAdd = (int) (size * bucket / bucketCount);
            add = (int) BitBuffer.unfoldSigned(buffer.readNumber(pos, bitsPerEntry)) + expectedAdd;
            pos += bitsPerEntry;
            if (Settings.COMPLEX_BUCKET_HEADER) {
                long db = expectedAdd == 0 ? dataBits : (dataBits * add / expectedAdd);
                int expectedStart = (int) (db * bucket / bucketCount);
                start = (int) BitBuffer.unfoldSigned(buffer.readNumber(pos, bitsPerEntry2)) + expectedStart;
            } else {
                int expectedStart = (int) (dataBits * bucket / bucketCount);
                start = (int) BitBuffer.unfoldSigned(buffer.readNumber(pos, bitsPerEntry2)) + expectedStart;
            }
            pos += bitsPerEntry2;
        }
        int bucketSize;
        if (bucket < bucketCount - 1) {
            int expectedAdd = (int) (size * (bucket + 1) / bucketCount);
            int nextAdd = (int) BitBuffer.unfoldSigned(buffer.readNumber(pos, bitsPerEntry)) + expectedAdd;
            bucketSize = nextAdd - add;
        } else {
            bucketSize = (int) (size - add);
        }
        if (bucketCount > 0) {
            pos = headerBits + start;
        }
        return add + evaluate(pos, obj, hashCode, 0, 0, bucketSize);
    }

    private int skip(int pos, int size) {
        if (size < 2) {
            return pos;
        }
        pos = buffer.skipGolombRice(pos, settings.getGolombRiceShift(size));
        if (size <= settings.getLeafSize()) {
            return pos;
        }
        int split = settings.getSplit(size);
        int firstPart, otherPart;
        if (split < 0) {
            firstPart = -split;
            otherPart = size - firstPart;
            split = 2;
        } else {
            firstPart = size / split;
            otherPart = firstPart;
        }
        int s = firstPart;
        for (int i = 0; i < split; i++) {
            pos = skip(pos, s);
            s = otherPart;
        }
        return pos;
    }

    private int evaluate(int pos, T obj, long hashCode,
            long index, int add, int size) {
        while (true) {
            if (size < 2) {
                return add;
            }
            int shift = settings.getGolombRiceShift(size);
            long q = buffer.readUntilZero(pos);
            pos += q + 1;
            long value = (q << shift) | buffer.readNumber(pos, shift);
            pos += shift;
            long oldX = Settings.getUniversalHashIndex(index);
            index += value + 1;
            long x = Settings.getUniversalHashIndex(index);
            if (x != oldX) {
                hashCode = hash.universalHash(obj, x);
            }
            if (size <= settings.getLeafSize()) {
                int h = Settings.supplementalHash(hashCode, index);
                h = Settings.scaleSmallSize(h, size);
                return add + h;
            }
            int split = settings.getSplit(size);
            int firstPart, otherPart;
            if (split < 0) {
                firstPart = -split;
                otherPart = size - firstPart;
                split = 2;
            } else {
                firstPart = size / split;
                otherPart = firstPart;
            }
            int h = Settings.supplementalHash(hashCode, index);
            if (firstPart != otherPart) {
                h = Settings.scaleInt(h, size);
                if (h < firstPart) {
                    size = firstPart;
                    continue;
                }
                pos = skip(pos, firstPart);
                add += firstPart;
                size = otherPart;
                continue;
            }
            h = Settings.scaleSmallSize(h, split);
            for (int i = 0; i < h; i++) {
                pos = skip(pos, firstPart);
                add += firstPart;
            }
            size = firstPart;
        }
    }

}
