import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for generating only acute myeloid leukemia focused patients.
 *
 * <p>This class reuses {@link App} command-line handling and generator behavior,
 * while ensuring the "acute_myeloid_leukemia" module is enabled.</p>
 */
public class AcuteMyeloidLeukemiaApp extends App {

  public static final String MODULE_NAME = "acute_myeloid_leukemia";
  public static final String CLASS_FLAG = "-class";
  public static final String START_DATE_FLAG = "-start_date";
  public static final String END_DATE_FLAG = "-end_date";

  /**
   * Normalize AML subclass args.
   *
   * <p>Supported subclass conveniences:
   * <ul>
   *   <li>{@code -class aml} (accepted and removed)</li>
   *   <li>{@code -start_date YYYYMMDD} (mapped to {@code -r})</li>
   *   <li>{@code -end_date YYYYMMDD} (mapped to {@code -e})</li>
   * </ul>
   *
   * @param args Original command-line args.
   * @return Updated args compatible with {@link App} parsing.
   */
  static String[] normalizeArgs(String[] args) {
    List<String> normalized = new ArrayList<>();
    if (args == null) {
      return new String[0];
    }

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equalsIgnoreCase(CLASS_FLAG)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(CLASS_FLAG + " requires a value");
        }
        String classValue = args[++i];
        if (!classValue.equalsIgnoreCase("aml")) {
          throw new IllegalArgumentException("Unsupported class '" + classValue
              + "'. Supported values: aml");
        }
      } else if (arg.equalsIgnoreCase(START_DATE_FLAG)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(START_DATE_FLAG + " requires a value");
        }
        normalized.add("-r");
        normalized.add(args[++i]);
      } else if (arg.equalsIgnoreCase(END_DATE_FLAG)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(END_DATE_FLAG + " requires a value");
        }
        normalized.add("-e");
        normalized.add(args[++i]);
      } else {
        normalized.add(arg);
      }
    }

    return normalized.toArray(new String[0]);
  }

  /**
   * Run Synthea generation constrained to the acute myeloid leukemia module.
   *
   * @param args Original command line args.
   * @throws Exception On errors.
   */
  public static void main(String[] args) throws Exception {
    App.main(withModule(normalizeArgs(args), MODULE_NAME));
  }
}
