package org.luc4ir.genutils;
import java.util.*;

public class UniqueSampler {
    static <T> List<T> sample(List<T> elements, int k) {
        List<T> samples = new ArrayList<>(k);
        if (k >= elements.size())
            return null;

        for (int i=0; i < k; i++) {
            Collections.shuffle(elements);
            samples.add(elements.remove(elements.size()-1));
        }
        return samples;
    }

    public static void main(String[] args) {
        List<Integer> A = new ArrayList<>();
        for (int i=0; i < 50; i++) {
            A.add(i);
        }

        UniqueSampler.sample(A, 10).stream().forEach(System.out::println);
    }
}
