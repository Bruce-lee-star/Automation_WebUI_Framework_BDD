import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {
            "com.hsbc.cmb.hk.dbb.automation.tests.glue",
        },
        plugin = {
            "pretty",
            "html:target/cucumber-report.html",
            "json:target/cucumber-report.json",
            "rerun:target/rerun.txt"
        },
        dryRun = false,
        tags = "@test"
)
public class CucumberTestRunnerIT {


}
