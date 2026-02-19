import java.util.Arrays;

/**
 * Central command-line entrypoint that dispatches to a specific launcher class.
 *
 * <p>If no {@code -class} argument is supplied, this defaults to {@link App}.
 * Supported class selectors:
 * <ul>
 *   <li>{@code aml} -> {@link AcuteMyeloidLeukemiaApp}</li>
 * </ul>
 */
public final class PointOfEntry {

  private PointOfEntry() {
  }

  static String extractClassName(String[] args) {
    if (args == null) {
      return null;
    }

    for (int i = 0; i < args.length; i++) {
      if ("-class".equalsIgnoreCase(args[i])) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("-class requires a value");
        }
        return args[i + 1];
      }
    }

    return null;
  }

  /**
   * Dispatch command line arguments to a concrete launcher class.
   *
   * @param args Command line args.
   * @throws Exception On errors.
   */
  public static void main(String[] args) throws Exception {
    String className = extractClassName(args);

    if (className == null || className.isEmpty()) {
      App.main(args);
      return;
    }

    if (className.equalsIgnoreCase("aml")) {
      AcuteMyeloidLeukemiaApp.main(args);
      return;
    }

    throw new IllegalArgumentException(
        "Unsupported class '" + className + "'. Supported values: " + Arrays.asList("aml"));
  }
}
