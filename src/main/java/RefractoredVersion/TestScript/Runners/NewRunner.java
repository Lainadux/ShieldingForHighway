//package RefractoredVersion.TestScript.Runners;
//
//import RefractoredVersion.TestScript.Config.Config;
//import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
//import RefractoredVersion.TestScript.Config.TestFunction;
//import RefractoredVersion.TestScript.Execution.BatchSimulation;
//import RefractoredVersion.TestScript.Execution.RecoverSimulation;
//import RefractoredVersion.TestScript.Execution.SingleSimulation;
//import RefractoredVersion.TestScript.Logging.GenLogReportWriter;
//import RefractoredVersion.TestScript.Logging.LogDirectory;
//import RefractoredVersion.TestScript.Logging.RunMetadataStore;
//
//public class NewRunner {
//    private final LogDirectory logDirectory = new LogDirectory();
//    private final RunMetadataStore metadataStore = new RunMetadataStore(logDirectory);
//    private final GenLogReportWriter reports = new GenLogReportWriter(logDirectory);
//
//    private final SingleSimulation singleSimulation = new SingleSimulation();
//    private final RecoverSimulation recoverSimulation = new RecoverSimulation(metadataStore);
//    private final BatchSimulation batchSimulation =
//            new BatchSimulation(singleSimulation, metadataStore, reports);
//
//    public void run(Config config) throws Exception {
//        if (!(config instanceof JavaMomentumConfig javaMomentumConfig)) {
//            return;
//        }
//
//        if (javaMomentumConfig.getTestFunction() == TestFunction.RECOVER) {
//            recoverSimulation.run(javaMomentumConfig);
//            return;
//        }
//
//        if (javaMomentumConfig.getTestFunction() == TestFunction.GEN_LOGS) {
//            batchSimulation.run(javaMomentumConfig);
//            return;
//        }
//
//        boolean rendered = javaMomentumConfig.getTestFunction() == TestFunction.QUICK_TEST;
//        singleSimulation.run(javaMomentumConfig, rendered, false);
//    }
//}
