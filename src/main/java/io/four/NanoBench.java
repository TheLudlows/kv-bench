package io.four;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.four.utils.restoreJvm;


/**
 * Lightweight CPU and memory benchmarking utility. <p> Inspired from nanobench
 * (http://code.google.com/p/nanobench/)
 */
public class NanoBench {

    public static NanoBench create() {
        return new NanoBench();
    }

    private static final Logger logger = Logger.getLogger(NanoBench.class.getSimpleName());
    private int numberOfMeasurement = 50;
    private int numberOfWarmUp = 20;
    private List<MeasureListener> listeners;

    public NanoBench() {
        listeners = new ArrayList(2);
        listeners.add(new CPUMeasureListener(logger));
        listeners.add(new MemoryUsageListener(logger));
    }

    public NanoBench measurements(int numberOfMeasurement) {
        this.numberOfMeasurement = numberOfMeasurement;
        return this;
    }

    public NanoBench warmUps(int numberOfWarmups) {
        this.numberOfWarmUp = numberOfWarmups;
        return this;
    }

    public NanoBench cpuAndMemory() {
        listeners = new ArrayList(2);
        listeners.add(new CPUMeasureListener(logger));
        listeners.add(new MemoryUsageListener(logger));
        return this;
    }

    public NanoBench bytesOnly() {
        listeners = new ArrayList(1);
        listeners.add(new BytesMeasure(logger));
        return this;
    }

    public MeasureListener getCPUListener() {
        return listeners.get(0);
    }

    public static Logger getLogger() {
        return logger;
    }

    public NanoBench cpuOnly() {
        listeners = new ArrayList(1);
        listeners.add(new CPUMeasureListener(logger));
        return this;
    }

    public NanoBench memoryOnly() {
        listeners = new ArrayList(1);
        listeners.add(new MemoryUsageListener(logger));
        return this;
    }

    public double getAvgTime() {
        CPUMeasureListener cpuMeasure = getCPUMeasure();
        return cpuMeasure.getFinalAvg();
    }

    public double getTotalTime() {
        CPUMeasureListener cpuMeasureListener = getCPUMeasure();
        return cpuMeasureListener.getFinalTotal();
    }

    public double getTps() {
        CPUMeasureListener cpuMeasureListener = getCPUMeasure();
        return cpuMeasureListener.getFinalTps();
    }

    public long getMemoryBytes() {
        MemoryUsageListener memoryUsageListener = getMemoryUsage();
        return memoryUsageListener.getFinalBytes();
    }

    private MemoryUsageListener getMemoryUsage() {
        MeasureListener listener = null;
        for (MeasureListener ml : listeners) {
            if (ml instanceof MemoryUsageListener) {
                listener = ml;
            }
        }
        if (listener == null) {
            throw new RuntimeException("Can't find memory measures");
        }
        return (MemoryUsageListener) listener;
    }

    private CPUMeasureListener getCPUMeasure() {
        MeasureListener listener = null;
        for (MeasureListener ml : listeners) {
            if (ml instanceof CPUMeasureListener) {
                listener = ml;
            }
        }
        if (listener == null) {
            throw new RuntimeException("Can't find CPU measures");
        }
        return (CPUMeasureListener) listener;
    }

    public void measure(String label, Runnable task) {
        restoreJvm();
        doWarmup(task);
        restoreJvm();
        doMeasure(label, task);
        restoreJvm();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void doMeasure(String label, Runnable task) {
        for (int i = 0; i < this.numberOfMeasurement; i++) {
            new MeasureProxy(new MeasureState(label, i, this.numberOfMeasurement), task, listeners).run();
        }
    }

    private void doWarmup(Runnable task) {
        for (int i = 0; i < this.numberOfWarmUp; i++) {
            new MeasureProxy(new MeasureState("_warmup_", i, this.numberOfWarmUp), task, listeners).run();
        }
    }

    /**
     * Decorated runnable which enables measurements.
     */
    private static class MeasureProxy implements Runnable {

        private MeasureState state;
        private Runnable runnable;
        private List<MeasureListener> listeners;

        public MeasureProxy(MeasureState state, Runnable runnable, List<MeasureListener> listeners) {
            super();
            this.state = state;
            this.runnable = runnable;
            this.listeners = listeners;
        }

        @Override
        public void run() {
            this.state.startNow();
            this.runnable.run();
            this.state.endNow();
            if (runnable instanceof BytesRunnable) {
                this.state.bytesMeasure = ((BytesRunnable) runnable).getMeasure();
            }
            if (!state.getLabel().equals("_warmup_")) {
                notifyMeasurement(state);
            }
        }

        private void notifyMeasurement(MeasureState times) {
            for (MeasureListener listener : this.listeners) {
                listener.onMeasure(times);
            }
        }
    }

    /**
     * Interface for measure listeners. Measure listeners are called when a
     * measurement is finished.
     */
    private interface MeasureListener {

        void onMeasure(MeasureState state);
    }

    public static abstract class BytesRunnable implements Runnable {

        protected int measure;

        public void run() {
            measure = runMeasure();
        }

        public abstract int runMeasure();

        public int getMeasure() {
            return measure;
        }
    }

    /**
     * Basic class to measure time spent in each measurement
     */
    private static class MeasureState implements Comparable<MeasureState> {

        private String label;
        private long startTime;
        private long endTime;
        private long index;
        private int measurement;
        private int bytesMeasure;

        public MeasureState(String label, long index, int measurement) {
            super();
            this.label = label;
            this.measurement = measurement;
            this.index = index;
        }

        public long getIndex() {
            return index;
        }

        public String getLabel() {
            return label;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public long getMeasurements() {
            return measurement;
        }

        public long getMeasureTime() {
            return endTime - startTime;
        }

        public void startNow() {
            this.startTime = System.nanoTime();
        }

        public void endNow() {
            this.endTime = System.nanoTime();
        }

        public int getBytesMeasure() {
            return bytesMeasure;
        }

        @Override
        public int compareTo(MeasureState another) {
            if (this.startTime > another.startTime) {
                return -1;
            } else if (this.startTime < another.startTime) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * CPU time listener to calculate the average time spent in a measurement.
     * <p> The listener is called at the end of each measurement and collect the
     * time spent from the
     * <code>MeasureState</code> instance. At the last measurement it shows the
     * average time spent, the total time and the number of measurement per
     * seconds.
     */
    public static class CPUMeasureListener implements MeasureListener {

        private static final double BY_SECONDS = 1000000000.0;
        private final Logger log;
        private static final DecimalFormat decimalFormat = new DecimalFormat("#,##0.0000");
        private static final DecimalFormat integerFormat = new DecimalFormat("#,##0.0");
        private int count = 0;
        private long timeUsed = 0;
        // Final
        private double finalTps;
        private double finalAvg;
        private double finalTotal;

        public CPUMeasureListener(Logger logger) {
            this.log = logger;
        }

        @Override
        public void onMeasure(MeasureState state) {
            count++;
            outputMeasureInfo(state);
        }

        private void outputMeasureInfo(MeasureState state) {
            timeUsed += state.getMeasureTime();

            if (isEnd(state)) {
                long total = timeUsed;

                finalAvg = total / state.getMeasurements() / 1000000.0;
                finalTotal = total / 1000000000.0;
                finalTps = state.getMeasurements() / (total / BY_SECONDS);

                StringBuilder sb = new StringBuilder();
                sb.append(state.getLabel()).append("\t").append("avg: ").append(decimalFormat.format(finalAvg)).append(" ms\t")
                        .append("total: ").append(integerFormat.format(finalTotal)).append(" s\t").append("   tps: ")
                        .append(integerFormat.format(finalTps)).append("\t").append("running: ").append(count).append(" times");
                count = 0;
                timeUsed = 0;
                if (!state.getLabel().equals("_warmup_")) {
                    log.info(sb.toString());
                }
            }
        }

        public double getFinalAvg() {
            return finalAvg;
        }

        public double getFinalTotal() {
            return finalTotal;
        }

        public double getFinalTps() {
            return finalTps;
        }

        private boolean isEnd(MeasureState state) {
            return count == state.getMeasurements();
        }
    }

    private static class BytesMeasure implements MeasureListener {

        private final Logger log;
        private static final DecimalFormat integerFormat = new DecimalFormat("#,##0.0");
        private int count = 0;
        private long bytesUsed = 0;

        public BytesMeasure(Logger logger) {
            this.log = logger;
        }

        @Override
        public void onMeasure(MeasureState state) {
            count++;
            outputMeasureInfo(state);
        }

        private void outputMeasureInfo(MeasureState state) {
            bytesUsed += state.getBytesMeasure();

            if (isEnd(state)) {
                StringBuilder sb = new StringBuilder();
                sb.append("bytes-usage: ").append(state.getLabel()).append("\t").append(format((bytesUsed / count)))
                        .append(" Bytes\t").append(format((bytesUsed / count) / (1024.0 * 1024.0))).append(" Mb\n");
                count = 0;
                bytesUsed = 0;

                if (!state.getLabel().equals("_warmup_")) {
                    log.info(sb.toString());
                }
            }
        }

        private String format(double value) {
            return integerFormat.format(value);
        }

        private boolean isEnd(MeasureState state) {
            return count == state.getMeasurements();
        }
    }

    /**
     * Memory usage listener to calculate the average memory usage. <p> The
     * listener is called after each measurement and perform a full GC and
     * calculate free memory. At the last measurement it shows the average
     * memory usage.
     */
    private static class MemoryUsageListener implements MeasureListener {

        private final Logger log;
        private static final DecimalFormat integerFormat = new DecimalFormat("#,##0.000");
        private int count = 0;
        private long memoryUsed = 0;
        // Final
        private long finalBytes;

        public MemoryUsageListener(Logger logger) {
            this.log = logger;
        }

        @Override
        public void onMeasure(MeasureState state) {
            count++;
            outputMeasureInfo(state);
        }

        private void outputMeasureInfo(MeasureState state) {
            restoreJvm();
            memoryUsed += utils.memoryUsed();

            if (isEnd(state)) {
                finalBytes = memoryUsed / count;

                StringBuilder sb = new StringBuilder();
                sb.append("memory-usage: ").append(state.getLabel()).append("\t").append(format(finalBytes / (1024.0 * 1024.0)))
                        .append(" Mb\n");
                count = 0;
                memoryUsed = 0;

                if (!state.getLabel().equals("_warmup_")) {
                    log.info(sb.toString());
                }
            }
        }

        public long getFinalBytes() {
            return finalBytes;
        }

        private String format(double value) {
            return integerFormat.format(value);
        }

        private boolean isEnd(MeasureState state) {
            return count == state.getMeasurements();
        }
    }
}
