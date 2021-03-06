package org.minperf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Methods to estimate the time needed to generate a MPHF.
 */
public class TimeEstimator {

    public static void main(String... args) {
        test();
    }

    static String[] test() {
        ArrayList<FunctionInfo> bestList = new ArrayList<>();
        for (int leafSize = 4; leafSize < 18; leafSize++) {
            for (int avgBucketSize = 10; avgBucketSize < 2 * 1024; avgBucketSize *= 1.1) {
                double genTime = getExpectedGenerationTime(leafSize, avgBucketSize);
                double bitsPerKey = SpaceEstimator.getExpectedSpace(leafSize, avgBucketSize);
                // System.out.println(leafSize + " " + avgBucketSize + " " + genTime + " " + bitsPerKey);
                FunctionInfo info = new FunctionInfo();
                info.averageBucketSize = avgBucketSize;
                info.leafSize = leafSize;
                info.generateNanos = genTime * 1000000;
                info.bitsPerKey = bitsPerKey;
                boolean add = true;
                for (int i = 0; i < bestList.size(); i++) {
                    FunctionInfo b = bestList.get(i);
                    if (b.bitsPerKey < info.bitsPerKey && b.generateNanos < info.generateNanos) {
                        add = false;
                        break;
                    } else if (b.bitsPerKey > info.bitsPerKey && b.generateNanos > info.generateNanos) {
                        bestList.remove(i);
                        i--;
                    }
                }
                if (add) {
                    bestList.add(info);
                }
            }
        }
        double[] result = new double[20];
        String[] config = new String[2 * 20];
        Arrays.fill(result, Double.POSITIVE_INFINITY);
        FunctionInfo[] bestInfos = new FunctionInfo[20];
        for (int i = 0; i < bestList.size(); i++) {
            FunctionInfo b = bestList.get(i);
            int index = (int) (b.bitsPerKey * 10 - 15);
//            double r = Math.log(b.generateNanos);
            double r = b.generateNanos;
            if (r < result[index]) {
                result[index] = r;
                config[index + index] = "" + r;
                config[index + index + 1] = b.averageBucketSize + "/" + b.leafSize;
                bestInfos[index] = b;
            }
        }
        for(FunctionInfo b : bestInfos) {
            if (b != null) {
                System.out.println(" " + b.bitsPerKey + " " + b.generateNanos);
            }
        }
        return config;
    }

    public static double getExpectedEvaluationSupplementalHashCalls(int leafSize, int averageBucketSize) {
        // System.out.println(" Estimated space for leafSize " + leafSize + " /
        // averageBucketSize " + averageBucketSize);
        // System.out.println(" Bucket sizes");
        Settings s = new Settings(leafSize, averageBucketSize);
        double result = 0;
        for (int i = 0; i <= s.getMaxBucketSize(); i++) {
            double probBucketSize = Probability.getProbabilityOfBucketFallsIntoBinOfSize(averageBucketSize, i);
            if (probBucketSize <= 0) {
                continue;
            }
            double r = getExpectedEvaluationSupplementalHashCalls(s, i, 0);
            result += r * probBucketSize;
        }
        // System.out.println("averageBucketSize " + averageBucketSize + "
        // leafSize " + leafSize + " gen " + result);
        return result;
    }

    public static double getExpectedGenerationTime(int leafSize, int averageBucketSize) {
        // System.out.println(" Estimated space for leafSize " + leafSize + " /
        // averageBucketSize " + averageBucketSize);
        // System.out.println(" Bucket sizes");
        Settings s = new Settings(leafSize, averageBucketSize);
        double result = 0;
        for (int i = 0; i <= s.getMaxBucketSize(); i++) {
            double probBucketSize = Probability.getProbabilityOfBucketSize(averageBucketSize, i);
            if (probBucketSize <= 0) {
                continue;
            }
            double r = getExpectedGenerationTime(s, i, 0);
            result += r * probBucketSize;
        }
        // System.out.println("averageBucketSize " + averageBucketSize + "
        // leafSize " + leafSize + " gen " + result);
        return result;
    }

    private static double getExpectedGenerationTime(Settings s, int size, int indent) {
        if (size <= 1) {
            return 0;
        }
        // String spaces = new String(new char[2 + indent * 2]).replace((char)
        // 0, ' ');
        if (size <= s.getLeafSize()) {
            return getExpectedHashFunctionCalls(size);
        }
        int split = s.getSplit(size);
        double result = getExpectedGenerationTime(s, size, indent, split);
        return result;
    }

    private static double getExpectedEvaluationSupplementalHashCalls(Settings s, int size, int indent) {
        if (size <= 1) {
            return 0;
        }
        // String spaces = new String(new char[2 + indent * 2]).replace((char)
        // 0, ' ');
        if (size <= s.getLeafSize()) {
            return 1;
        }
        int split = s.getSplit(size);
        double result = getExpectedEvaluationSupplementalHashCalls(s, size, indent, split);
        return result;
    }

    private static double getExpectedEvaluationSupplementalHashCalls(Settings s, int size, int indent, int split) {
        double result = 1;
        if (split < 0) {
            double p = -split / (double) size;
            result += p * getExpectedEvaluationSupplementalHashCalls(s, -split, indent + 1);
            result += (1 - p) * getExpectedEvaluationSupplementalHashCalls(s, size + split, indent + 1);
        } else {
            double p = 1.0 / split;
            for (int i = 0; i < split; i++) {
                result += p * getExpectedEvaluationSupplementalHashCalls(s, size / split, indent + 1);
            }
        }
        return result;
    }

    private static double getExpectedGenerationTime(Settings s, int size, int indent, int split) {
        double p = SpaceEstimator.getSplitProbability(size, split);
        double result = size * (1 / p);
        if (split < 0) {
            result += getExpectedGenerationTime(s, -split, indent + 1);
            result += getExpectedGenerationTime(s, size + split, indent + 1);
        } else {
            for (int i = 0; i < split; i++) {
                result += getExpectedGenerationTime(s, size / split, indent + 1);
            }
        }
        return result;
    }

    public static double getExpectedHashFunctionCalls(int size) {
        double averageTries = 1 / Probability.probabilitySplitIntoMSubsetsOfSizeN(size, 1);
        double a = 1, b = 1;
        double last = 0;
        double result = 0;
        for (int i = 1; i <= size; i++) {
            a *= size - (i - 1);
            b *= size;
            double p = 1 - (a / b);
            result += i * (p - last) * averageTries;
            last = p;
        }
        result += size;
        return result;
    }

    private static double simulateExpectedHashFunctionCalls(int leafSize) {
        Random r = new Random(1);
        int tries = 1000000 / leafSize;
        long calls = 0;
        int[] conflictAt = new int[leafSize];
        for (int i = 0; i < tries; i++) {
            while (true) {
                int b = 0;
                int j = 0;
                for (; j < leafSize; j++) {
                    calls++;
                    int x = r.nextInt(leafSize);
                    if ((b & (1 << x)) != 0) {
                        conflictAt[j]++;
                        break;
                    }
                    b |= 1 << x;
                }
                if (j == leafSize) {
                    break;
                }
            }
        }
        int sum = 0;
        for (int x : conflictAt) {
            sum += x;
        }
        for (int i = 0; i < leafSize; i++) {
            System.out.println((i + 1) + " p " + (double) conflictAt[i] / sum);
        }
        return (double) calls / tries;
    }

    static double calcEstimatedHashCallsPerKey(long size, int split) {
        double p2 = size, p1 = split;
        return 0.3 * Math.pow(2.37, p1) * Math.pow(p2 / p1, 1 / (0.34 + (7 / Math.pow(p1, 2.1))));
    }

    static long calcEstimatedHashCallsPerKey(int leafSize) {
        double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(leafSize, 1);
        return (long) (3.267 * (Math.pow(-1 / Math.log(1 - p), 1.0457) / leafSize));
    }

}
