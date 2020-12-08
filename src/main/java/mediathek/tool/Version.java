package mediathek.tool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record Version(int major, int minor, int patch) {

    private static final Logger logger = LogManager.getLogger();

    public static Version fromString(String versionsstring) {
        Version result;

        final String[] versions = versionsstring.replaceAll("-SNAPSHOT", "").split("\\.");
        if (versions.length == 3) {
            try {
                result = new Version(Integer.parseInt(versions[0]), Integer.parseInt(versions[1]), Integer.parseInt(versions[2]));
            } catch (NumberFormatException ex) {
                logger.error("Fehler beim Parsen der Version: {}", versionsstring, ex);
                result = new Version(0, 0, 0);
            }
        } else
            result = new Version(0, 0, 0);

        return result;
    }

    /**
     * Gibt die Version als gewichtete Zahl zurück.
     *
     * @return gewichtete Zahl als Integer
     */
    private int toNumber() {
        return major * 100 + minor * 10 + patch;
    }

    /**
     * Gibt die Version als String zurück
     *
     * @return String mit der Version
     */
    @Override
    public String toString() {
        return String.format("%d.%d.%d", major, minor, patch);
    }

    /**
     * Nimmt ein Objekt vom Typ Version an und vergleicht ihn mit sich selbst
     *
     * @param versionzwei Versionsobjekt welches zum vergleich rangezogen werden soll
     * @return 1 Version a ist größer, 0 Versionen sind gleich oder -1 Version a ist kleiner
     */
    public int compare(Version versionzwei) {
        return Integer.compare(versionzwei.toNumber(), this.toNumber());
    }

}
