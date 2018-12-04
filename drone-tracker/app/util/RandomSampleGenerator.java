package util;

import com.typesafe.config.Config;

import java.util.Random;

public class RandomSampleGenerator {
    private float mean;
    private float stddev;
    private float floor;
    private Random random;

    /**
     * constructor
     * @param mean
     * @param stddev
     * @param floor
     * @param randomSeed
     */
    public RandomSampleGenerator(float mean, float stddev, float floor, long randomSeed) {
        init(mean, stddev, floor, randomSeed);
    }

    /**
     * constructor
     * @param config
     */
    public RandomSampleGenerator(Config config) {
        float floor;
        try {
            floor = (float)config.getDouble("floor");
        } catch (Exception ex) {
            floor = Float.MAX_VALUE * -1;
        }
        float mean = (float)config.getDouble("mean");
        float stddev = (float)config.getDouble("stddev");

        init(mean, stddev, floor, System.currentTimeMillis());
    }

    /**
     * constructor
     * @param mean
     * @param stddev
     * @param floor
     */
    public RandomSampleGenerator(float mean, float stddev, float floor) {
        init(mean, stddev, floor, System.currentTimeMillis());
    }

    /** private init */
    private void init(float mean, float stddev, float floor, long randomSeed) {
        this.mean = mean;
        this.stddev = stddev;
        this.floor = floor;
        this.random = new Random(randomSeed);
    }

    /** generates next random sample using a Gaussian distribution */
    public float nextSample() {
        return Math.max(this.floor, (float)this.random.nextGaussian() * this.stddev + this.mean);
    }
}
