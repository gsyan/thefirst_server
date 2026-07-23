package com.bk.sbs.util;

// SplitMix64 기반 결정론적 PRNG — 클라이언트(C#)와 동일한 seed에서 항상 동일한 결과를 내야 함
// java.util.Random과 System.Random(C#)은 내부 알고리즘이 달라 seed가 같아도 결과가 다르므로 직접 구현.
// 클라 대응 구현: Assets/Scripts/Exploration/CrossPlatformRandom.cs — 두 파일은 항상 함께 수정할 것
public class CrossPlatformRandom {

    private long state;

    public CrossPlatformRandom(int seed) {
        this.state = seed;
    }

    private long nextRawBits() {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // [0, maxExclusive) 반환 — maxExclusive<=0이면 0
    public int next(int maxExclusive) {
        if (maxExclusive <= 0) return 0;
        return (int) Long.remainderUnsigned(nextRawBits(), maxExclusive);
    }

    // [minInclusive, maxExclusive) 반환 — maxExclusive<=minInclusive면 minInclusive
    public int next(int minInclusive, int maxExclusive) {
        if (maxExclusive <= minInclusive) return minInclusive;
        return minInclusive + next(maxExclusive - minInclusive);
    }
}
