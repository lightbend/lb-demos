package util;

import com.google.common.collect.Lists;
import geocode.GeoName;
import geocode.ReverseGeoCode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

public class RandomSampleGeneratorTest {

    protected void testSampleHelper(float mean, float stddev, float floor, long seed) {
        RandomSampleGenerator sampleGen = new RandomSampleGenerator(mean, stddev, floor, seed);
        float sum = 0;
        ArrayList<Float> samples = new ArrayList<Float>();
        for (int i=0; i < 1000; i++) {
            float sample = sampleGen.nextSample();
            sum += sample;
            samples.add(sample);
        }
        float myMean = sum / samples.size();
        float sum_variance = 0;
        for (float sample: samples) {
            sum_variance += Math.pow(sample - mean, 2);
        }
        float myStddev = (float)Math.sqrt(sum_variance / samples.size());
        assertEquals(mean, myMean, 1f);
        assertEquals(stddev, myStddev, 1f);
    }

    @Test
    public void testSampleGenerator() {
        testSampleHelper(50, 25, Float.MIN_VALUE, 0);
    }

    @Test
    public void testSampleGeneratorZeroMean() {
        testSampleHelper(0, 50, Float.MAX_VALUE * -1, 0);
    }

    @Test
    public void testSampleGeneratorWithFloor() {
        RandomSampleGenerator sampleGen = new RandomSampleGenerator(50, 25, 0f, 0);
        for (int i=0; i < 1000; i++) {
            assertTrue(sampleGen.nextSample() >= 0f);
        }
    }

//    @Test
//    public void testFoo() {
//        Random r = new Random(0);
//        RandomSampleGenerator sampleGen = new RandomSampleGenerator(0, 2, Float.MIN_VALUE, 0);
//        for (int i=0; i < 1000; i++) {
//            //System.out.format("%.2f\n", r.nextGaussian());
//            System.out.format("%.2f\n", sampleGen.nextSample());
//        }
//        assertTrue(false);
//    }
}

