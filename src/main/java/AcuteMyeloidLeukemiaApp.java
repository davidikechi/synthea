/**
 * Entry point for generating only acute myeloid leukemia focused patients.
 *
 * <p>This class reuses {@link App} command-line handling and generator behavior,
 * while ensuring the "acute_myeloid_leukemia" module is enabled.</p>
 */
public class AcuteMyeloidLeukemiaApp extends App {

  public static final String MODULE_NAME = "acute_myeloid_leukemia";

  /**
   * Run Synthea generation constrained to the acute myeloid leukemia module.
   *
   * @param args Original command line args.
   * @throws Exception On errors.
   */
  public static void main(String[] args) throws Exception {
    App.main(withModule(args, MODULE_NAME));
  }
}
