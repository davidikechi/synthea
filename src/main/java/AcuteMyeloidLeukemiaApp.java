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
  public static final String GENDER_MIX_FLAG = "-gender";

  /**
   * Normalize AML subclass args.
   *
   * <p>Supported subclass conveniences:
   * <ul>
   *   <li>{@code -class aml} (accepted and removed)</li>
   *   <li>{@code -start_date YYYYMMDD} (mapped to {@code -r})</li>
   *   <li>{@code -end_date YYYYMMDD} (mapped to {@code -e})</li>
   *   <li>{@code -gender 0-100} (handled by AML launcher as male percentage)</li>
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

  static Integer extractMalePercentage(String[] args) {
    if (args == null) {
      return null;
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(GENDER_MIX_FLAG)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(GENDER_MIX_FLAG + " requires a value");
        }
        int malePercentage = Integer.parseInt(args[i + 1]);
        if (malePercentage < 0 || malePercentage > 100) {
          throw new IllegalArgumentException("Male percentage must be between 0 and 100.");
        }
        return malePercentage;
      }
    }

    return null;
  }

  static String[] removeGenderMixArg(String[] args) {
    List<String> filtered = new ArrayList<>();
    if (args == null) {
      return new String[0];
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase(GENDER_MIX_FLAG)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(GENDER_MIX_FLAG + " requires a value");
        }
        i++; // skip value
      } else {
        filtered.add(args[i]);
      }
    }

    return filtered.toArray(new String[0]);
  }

  static int extractPopulation(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-p")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("-p requires a value");
        }
        return Integer.parseInt(args[i + 1]);
      }
    }
    return 1;
  }

  static String[] withPopulationAndGender(String[] args, int population, String gender) {
    List<String> rewritten = new ArrayList<>();
    boolean skipNext = false;
    for (int i = 0; i < args.length; i++) {
      if (skipNext) {
        skipNext = false;
        continue;
      }
      String arg = args[i];
      if (arg.equalsIgnoreCase("-p") || arg.equalsIgnoreCase("-g")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException(arg + " requires a value");
        }
        skipNext = true;
        continue;
      }
      rewritten.add(arg);
    }

    rewritten.add("-p");
    rewritten.add(Integer.toString(population));
    rewritten.add("-g");
    rewritten.add(gender);
    return rewritten.toArray(new String[0]);
  }

  /**
   * Run Synthea generation constrained to the acute myeloid leukemia module.
   *
   * @param args Original command line args.
   * @throws Exception On errors.
   */
  public static void main(String[] args) throws Exception {
    String[] normalized = normalizeArgs(removeGenderMixArg(args));
    Integer malePercentage = extractMalePercentage(args);

    if (malePercentage == null) {
      App.main(withModule(normalized, MODULE_NAME));
      return;
    }

    int population = extractPopulation(normalized);
    int malePopulation = (int) Math.round(population * (malePercentage / 100.0));
    int femalePopulation = population - malePopulation;

    System.out.println(String.format("Gender Mix: %d%% male / %d%% female",
        malePercentage, 100 - malePercentage));

    if (malePopulation > 0) {
      App.main(withModule(withPopulationAndGender(normalized, malePopulation, "M"), MODULE_NAME));
    }
    if (femalePopulation > 0) {
      App.main(withModule(withPopulationAndGender(normalized, femalePopulation, "F"), MODULE_NAME));
    }
  }
}
