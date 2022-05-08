package trades;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

public class TestSuite {
    //private final static Logger log = LoggerFactory.getLogger(TestSuite.class);

    // Test all test cases
    @Suite
    @SelectPackages("trades.testCase")
    public static class TestAllTestSuite {
    }

    // Test all io test cases
    @Suite
    @SelectPackages("trades.testCase.io")
    public static class IOTestSuite {
    }

    // Test all consistency test cases
    @Suite
    @SelectPackages("trades.testCase.consistency")
    public static class ConsistencyTestSuite {
    }
}