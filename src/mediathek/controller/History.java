/*
 *    MediathekView
 *    Copyright (C) 2010 by Andreas M.
 *    http://zdfmediathk.sourceforge.net/
 *    
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import mediathek.daten.Daten;
import mediathek.daten.DatenDownload;
import mediathek.tool.ListenerMediathekView;
import msearch.daten.DatenFilm;

public class History extends HashSet<String> {

    private Path historyFilePath = null;

    public History() {
        try {
            historyFilePath = getHistoryFilePath();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean add(String url) {
        boolean ret = super.add(url);
        ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_LISTE_HISTORY_GEAENDERT, History.class.getSimpleName());
        return ret;
    }

    public void add(ArrayList<DatenDownload> ad) {
        for (DatenDownload d : ad) {
            if (d.film != null) {
                Daten.listeFilmeHistory.add(d.film);
            } else {
                System.out.print("Mist");
            }
            super.add(d.arr[DatenDownload.DOWNLOAD_HISTORY_URL_NR]);
        }
        ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_LISTE_HISTORY_GEAENDERT, History.class.getSimpleName());
    }

    @Override
    public boolean remove(Object url) {
        boolean ret = super.remove(url);
        ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_LISTE_HISTORY_GEAENDERT, History.class.getSimpleName());
        return ret;
    }

    /**
     * Get the Path to the history file
     *
     * @return Path object to history file
     */
    private Path getHistoryFilePath() throws IOException {
        return Daten.getSettingsDirectory().resolve("history.txt");
    }

    public void laden() {
        clear();

        if (Files.notExists(historyFilePath)) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(Files.newInputStream(historyFilePath))))) {
            String strLine;
            while ((strLine = br.readLine()) != null) {
                super.add(strLine);
            }
            br.close();
            ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_LISTE_HISTORY_GEAENDERT, History.class.getSimpleName());
        } catch (Exception e) {
            System.err.println("Fehler: " + e);
            Log.fehlerMeldung(303049876, Log.FEHLER_ART_PROG, History.class.getName(), e);
        }
    }

    public void speichern() {
        try (BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new DataOutputStream(Files.newOutputStream(historyFilePath))))) {
            for (String h : this) {
                br.write(h + "\n");
            }

            br.flush();
            br.close();
        } catch (Exception e) {//Catch exception if any
            Log.fehlerMeldung(978786563, Log.FEHLER_ART_PROG, History.class.getName(), e);
        }
    }

    public void loeschen() {
        this.clear();

        if (Files.notExists(historyFilePath)) {
            return;
        }

        try {
            Files.delete(historyFilePath);
            ListenerMediathekView.notify(ListenerMediathekView.EREIGNIS_LISTE_HISTORY_GEAENDERT, History.class.getSimpleName());
        } catch (IOException ignored) {
        }
    }

    public Object[][] getObjectData() {
        int i = 0;
        Object[][] object = new Object[size()][3];
        for (String s : this) {
            DatenFilm film = Daten.listeFilme.getFilmByUrl(s);
            object[i][0] = "";
            object[i][1] = film != null ? film.arr[DatenFilm.FILM_TITEL_NR] : "";
            object[i][2] = s;
            ++i;
        }
        return object;
    }

}
