package edu.nd.cse.benchmarkcommon;

import java.io.File;
import java.util.UUID;

/**
 * Core information for the Benchmark Profile. This profile consists of a
 * single service that is the Benchmark Service. This service, in turn,
 * consists of the test characteristic as well as several characteristics
 * that correspond to the statistics captured by the profile. We are also
 * demonstrating here a different way of using GATT communication. Rather
 * than exposing GATT to the application, we instead only let the
 * application interact with the profile:
 * -----------
 * |   app   |
 * -----------
 * | profile |
 * -----------
 * |  GATT   |
 * -----------
 *
 * The server side exposes the following functionality to the application:
 * - start the profile
 * - stop the profile
 * - stop advertising after connect (x)
 * - log benchmark results to file
 *
 * The client side exposes the following functionality to the application:
 * - start the profile
 * - begin benchmarking on connect (x)
 * - stop the profile
 * - stop scanning after connect (x)
 * - start benchmarking (x)
 * - stop benchmarking
 * - set MTU (x)
 * - set connection priority (interval) (x)
 * - set data size (x)
 * - get raw timestamps (x)
 * - get throughput (x)
 * - get loss rate (x)
 *
 * Note that the (x) in the above lists indicates required interaction with at
 * least one other device.
 * */
public class BenchmarkProfile {

    protected final int MAX_DIFFS = 1000;


    public static final UUID BENCHMARK_SERVICE = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB");

    //Throughput benchmarking will occur on this characteristic (bytes)
    public static UUID TEST_CHAR = UUID.fromString("00000002-0000-1000-8000-00805F9B34FB");

    //descriptor for changing the behavior of the test characteristic (n/a)
    public static UUID TEST_DESC = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

    //The following characteristics are available for querying about the results of the
    //benchmarking. Note that querying these chars DURING the benchmark WILL effect the
    //the results.

    //used to stream inter-packet timestamps back to client (string)
    //characteristic must be made available, but does not need to be implemented
    //if not implemented simply return a single byte: 0
    public static UUID RAW_DATA_CHAR = UUID.fromString("00000004-0000-1000-8000-00805F9B34FB");

    //compute and return the throughput in bits per second (int)
    public static UUID THROUGHPUT_CHAR = UUID.fromString("00000005-0000-1000-8000-00805F9B34FB");

    //using the connection interval, mtu, and data size, compute and return the loss rate as a
    //percentage (float)
    public static UUID LOSS_RATE_CHAR = UUID.fromString("00000006-0000-1000-8000-00805F9B34FB");
}
