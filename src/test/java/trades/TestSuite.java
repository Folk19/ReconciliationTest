package trades;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

public class TestSuite {
    // Test all test cases
    @Suite
    @SelectPackages("trades.testCase")
    public static class TestAllTestSuite {
    }

    // Test all consistency test cases
    @Suite
    @SelectPackages("trades.testCase.consistency")
    public static class ConsistencyTestSuite {
    }
}