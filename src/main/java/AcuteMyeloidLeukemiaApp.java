import java.io.File;

/**
 * Entry point for generating only acute myeloid leukemia focused patients.
 *
 * <p>All flag parsing (including {@code -class}, {@code -start_date}, {@code -end_date},
 * {@code -gender}, {@code -gr}, etc.) is handled by {@link App}.
 * This class simply ensures the AML module is injected into the module list
 * before delegating to {@link App#main(String[])}.</p>
 *
 * <p>Module aliases: {@code aml_model} is resolved to {@code aml_disease_model}.</p>
 */
public class AcuteMyeloidLeukemiaApp extends App {

  /** Default AML module loaded when no {@code -m} flag is supplied. */
  public static final String MODULE_NAME = "acute_myeloid_leukemia";

  /**
   * The canonical name of the full AML disease model module (aml_disease_model.json).
   * When a user passes {@code -m aml_model}, this alias is resolved to this name.
   */
  public static final String AML_DISEASE_MODULE_NAME = "aml_disease_model";

  /**
   * Short alias {@code aml_model} that resolves to {@link #AML_DISEASE_MODULE_NAME}.
   */
  static final java.util.Set<String> AML_MODULE_ALIASES = new java.util.HashSet<>(
      java.util.Arrays.asList("aml_model"));

  /**
   * Resolve known AML module name aliases within a {@code -m} value string.
   *
   * <p>Each token in the path-separator-delimited list is checked against
   * {@link #AML_MODULE_ALIASES}. Matching tokens are replaced with
   * {@link #AML_DISEASE_MODULE_NAME} so that {@code aml_model} correctly
   * loads the full {@code aml_disease_model.json} module.
   *
   * @param moduleValue Raw value string from a {@code -m} argument.
   * @return Updated value string with aliases replaced.
   */
  static String resolveModuleAliases(String moduleValue) {
    String[] tokens = moduleValue.split(java.io.File.pathSeparator);
    StringBuilder resolved = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      if (i > 0) {
        resolved.append(File.pathSeparator);
      }
      String token = tokens[i];
      resolved.append(AML_MODULE_ALIASES.contains(token.toLowerCase())
          ? AML_DISEASE_MODULE_NAME : token);
    }
    return resolved.toString();
  }

  /**
   * Resolve module aliases in any {@code -m} argument and return the updated args array.
   *
   * @param args Original command-line args.
   * @return Args with AML module aliases replaced by canonical names.
   */
  static String[] resolveAliasesInArgs(String[] args) {
    if (args == null) {
      return new String[0];
    }
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].equalsIgnoreCase("-m") && i + 1 < result.length) {
        result[i + 1] = resolveModuleAliases(result[i + 1]);
      }
    }
    return result;
  }

  /**
   * Run Synthea generation constrained to the acute myeloid leukemia module.
   *
   * <p>All flags ({@code -class}, {@code -start_date}, {@code -end_date}, {@code -gender},
   * {@code -gr}, {@code -g}, {@code -p}, etc.) are handled by {@link App#main(String[])}.
   * If no {@code -m} flag is present, the default AML module is automatically added.</p>
   *
   * @param args Command-line args (passed through to {@link App}).
   * @throws Exception On errors.
   */
  public static void main(String[] args) throws Exception {
    String[] resolved = resolveAliasesInArgs(args);
    App.main(hasModuleFilter(resolved) ? resolved : withModule(resolved, MODULE_NAME));
  }

  /**
   * Returns true if a {@code -m} flag is present in args.
   */
  static boolean hasModuleFilter(String[] args) {
    if (args == null) {
      return false;
    }
    for (String arg : args) {
      if (arg.equalsIgnoreCase("-m")) {
        return true;
      }
    }
    return false;
  }
}
