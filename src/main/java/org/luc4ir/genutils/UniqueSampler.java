package org.luc4ir.genutils;
import java.util.*;
import java.util.stream.Collectors;

public class UniqueSampler {
    static <T> List<T> sample(List<T> elements_orig, int k) {
        List<T> elements = new ArrayList<T>(elements_orig);
        List<T> samples = new ArrayList<>(k);
        if (k > elements.size())
            return null;

        for (int i=0; i < k; i++) {
            Collections.shuffle(elements);
            samples.add(elements.remove(elements.size()-1));
        }
        return samples;
    }

    static <T> List<T> sample(List<T> elements_orig, List<Double> weights_orig, int k) {
        List<T> elements = new ArrayList<T>(elements_orig);
        List<Double> weights = new ArrayList<Double>(weights_orig);

        List<T> samples = new ArrayList<>(k);
        if (k > elements.size())
            return null;

        for (int j=0; j < k; j++) {
            double sum = weights.stream().reduce(0.0, (a, b) -> a + b);
            double[] probs = weights.stream().map(x -> x / sum).mapToDouble(d -> d).toArray();

            double x = Math.random();
            double csum = probs[0];

            int i;
            for (i=0; i < probs.length-1; i++) {
                if (x < csum)
                    break;
                csum += probs[i];
            }

            // chosen index = i
            samples.add(elements.remove(i));
            weights.remove(i);
        }

        return samples;
    }

    public static void main(String[] args) {
        final int N = 10;
        final int k = 10;
        List<Integer> A = new ArrayList<>();
        for (int i=1; i <= N; i++) {
            A.add(i);
        }

        System.out.println("With uniform sampling");
        List<Integer> samples = UniqueSampler.sample(A, k).stream().collect(Collectors.toList());
        System.out.println(samples);

        List<Double> w = new ArrayList<>();
        for (int i=1; i <= N; i++) {
            w.add((double)i);
        }

        System.out.println("With weighted sampling");
        samples = UniqueSampler.sample(A, w, k).stream().collect(Collectors.toList());
        System.out.println(samples);
    }
}
