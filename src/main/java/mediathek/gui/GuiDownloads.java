/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.gui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TabPane;
import jiconfont.icons.FontAwesome;
import jiconfont.swing.IconFontSwing;
import mSearch.daten.DatenFilm;
import mSearch.filmeSuchen.ListenerFilmeLaden;
import mSearch.filmeSuchen.ListenerFilmeLadenEvent;
import mSearch.tool.ApplicationConfiguration;
import mSearch.tool.Datum;
import mSearch.tool.Listener;
import mSearch.tool.Log;
import mediathek.MediathekGui;
import mediathek.config.Daten;
import mediathek.config.Icons;
import mediathek.config.Konstanten;
import mediathek.config.MVConfig;
import mediathek.controller.history.MVUsedUrl;
import mediathek.controller.starter.Start;
import mediathek.daten.DatenAbo;
import mediathek.daten.DatenDownload;
import mediathek.daten.DatenPset;
import mediathek.gui.actions.ShowFilmInformationAction;
import mediathek.gui.dialog.DialogBeendenZeit;
import mediathek.gui.dialog.DialogEditAbo;
import mediathek.gui.dialog.DialogEditDownload;
import mediathek.gui.dialog.StandardCloseDialog;
import mediathek.gui.dialogEinstellungen.PanelErledigteUrls;
import mediathek.gui.messages.DownloadRateLimitChangedEvent;
import mediathek.gui.messages.GeoStateChangedEvent;
import mediathek.gui.messages.StartEvent;
import mediathek.gui.messages.UpdateStatusBarLeftDisplayEvent;
import mediathek.gui.toolbar.FXDownloadToolBar;
import mediathek.javafx.DownloadTabInformationLabel;
import mediathek.javafx.descriptionPanel.DescriptionPanelController;
import mediathek.tool.*;
import mediathek.tool.cellrenderer.CellRendererDownloads;
import mediathek.tool.listener.BeobTableHeader;
import mediathek.tool.table.MVDownloadsTable;
import mediathek.tool.table.MVTable;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("serial")
public class GuiDownloads extends JPanel {
    private long lastUpdate = 0;
    private boolean onlyAbos = false;
    private boolean onlyDownloads = false;
    private boolean onlyWaiting = false;
    private boolean onlyNotStarted = false;
    private boolean onlyStarted = false;
    private boolean onlyFinished = false;
    private boolean onlyRun = false;
    private static final String COMBO_DISPLAY_ALL = "alles";
    private static final String COMBO_DISPLAY_DOWNLOADS_ONLY = "nur Downloads";
    private static final String COMBO_DISPLAY_ABOS_ONLY = "nur Abos";

    private static final String COMBO_VIEW_ALL = "alles ";
    private static final String COMBO_VIEW_NOT_STARTED = "nicht gestartet";
    private static final String COMBO_VIEW_STARTED = "gestartet";
    private static final String COMBO_VIEW_WAITING = "nur wartende";
    private static final String COMBO_VIEW_RUN_ONLY = "nur laufende";
    private static final String COMBO_VIEW_FINISHED_ONLY = "nur abgeschlossene";
    private boolean loadFilmlist = false;
    private final java.util.Timer timer = new java.util.Timer(false);
    private TimerTask timerTask = null;
    private final MVTable tabelle;
    public static final String NAME = "Downloads";
    private final Daten daten;
    private final JFrame parentComponent;



    /**
     * The internally used model.
     */
    private TModelDownload model;

    private void setupF4Key(MediathekGui mediathekGui) {
        if (SystemUtils.IS_OS_WINDOWS) {
            // zum Abfangen der Win-F4 für comboboxen
            InputMap im = cbDisplayCategories.getInputMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "einstellungen");
            ActionMap am = cbDisplayCategories.getActionMap();
            am.put("einstellungen", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    mediathekGui.showSettingsDialog();
                }
            });
        }
    }

    /**
     * Update the property with the current number of selected entries from the JTable.
     */
    private void setupFilmSelectionPropertyListener(MediathekGui mediathekGui) {
        tabelle.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                final int sel = tabelle.getSelectedRowCount();
                Platform.runLater(() -> mediathekGui.getSelectedItemsProperty().setValue(sel));
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                final int sel = tabelle.getSelectedRowCount();
                Platform.runLater(() -> mediathekGui.getSelectedItemsProperty().setValue(sel));
                onComponentShown();
            }
        });
    }

    private DownloadTabInformationLabel filmInfoLabel;

    private void installTabInfoStatusBarControl() {
        Platform.runLater(() -> {
            filmInfoLabel = new DownloadTabInformationLabel(daten);
            if (isVisible())
                MediathekGui.ui().getStatusBarController().getStatusBar().getLeftItems().add(filmInfoLabel);
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                Platform.runLater(() -> {
                    filmInfoLabel.setVisible(true);
                    MediathekGui.ui().getStatusBarController().getStatusBar().getLeftItems().add(filmInfoLabel);
                });
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                Platform.runLater(() -> {
                    filmInfoLabel.setVisible(false);
                    MediathekGui.ui().getStatusBarController().getStatusBar().getLeftItems().remove(filmInfoLabel);
                });
            }
        });
    }

    public GuiDownloads(Daten aDaten, MediathekGui mediathekGui) {
        super();
        daten = aDaten;
        parentComponent = mediathekGui;

        initComponents();

        setupF4Key(mediathekGui);

        tabelle = new MVDownloadsTable();
        jScrollPane1.setViewportView(tabelle);

        setupDescriptionPanel();

        showDescriptionPanel();

        init();

        installTabInfoStatusBarControl();

        setupFilmSelectionPropertyListener(mediathekGui);

        tabelle.initTabelle();
        tabelle.setSpalten();
        if (tabelle.getRowCount() > 0) {
            tabelle.setRowSelectionInterval(0, 0);
        }

        addListenerMediathekView();
        cbDisplayCategories.setModel(getDisplaySelectionModel());
        cbDisplayCategories.addActionListener(new DisplayCategoryListener());

        cbView.setModel(getViewModel());
        cbView.addActionListener(new DisplayCategoryListener());

        JFXPanel toolBarPanel = new JFXPanel();
        add(toolBarPanel,BorderLayout.NORTH);
        Platform.runLater(() -> toolBarPanel.setScene(new Scene(new FXDownloadToolBar(this))));

        setupDownloadRateLimitSpinner();
    }

    private void setupDownloadRateLimitSpinner() {
        //restore spinner setting from config
        final int oldDownloadLimit = ApplicationConfiguration.getConfiguration().getInt(ApplicationConfiguration.DOWNLOAD_RATE_LIMIT, 0);
        jSpinner1.setValue(oldDownloadLimit);

        jSpinner1.addChangeListener(e -> {
            final int downloadLimit = (int) jSpinner1.getValue();
            logger.info("Saving download rate limit {} to config", downloadLimit);
            ApplicationConfiguration.getConfiguration().setProperty(ApplicationConfiguration.DOWNLOAD_RATE_LIMIT, downloadLimit);
            DownloadRateLimitChangedEvent evt = new DownloadRateLimitChangedEvent();
            evt.newLimit = downloadLimit;
            daten.getMessageBus().publishAsync(evt);
        });
    }

    private final JCheckBoxMenuItem cbShowDownloadDescription = new JCheckBoxMenuItem("Filmbeschreibung anzeigen");

    public void installMenuEntries(JMenu menu) {
        JMenuItem miDownloadsStartAll = new JMenuItem("Alle Downloads starten");
        miDownloadsStartAll.setIcon(IconFontSwing.buildIcon(FontAwesome.ANGLE_DOUBLE_DOWN, 16));
        miDownloadsStartAll.addActionListener(e -> starten(true));

        JMenuItem miDownloadStartTimed = new JMenuItem("Alle Downloads um xx:yy Uhr starten");
        miDownloadStartTimed.setIcon(Icons.ICON_MENUE_DOWNLOAD_ALLE_STARTEN);
        miDownloadStartTimed.addActionListener(e -> startAtTime());

        JMenuItem miStopAllDownloads = new JMenuItem("Alle Downloads stoppen");
        miStopAllDownloads.addActionListener(e -> stoppen(true ));

        JMenuItem miStopWaitingDownloads = new JMenuItem("Wartende Downloads stoppen");
        miStopWaitingDownloads.addActionListener(e -> stopAllWaitingDownloads());

        JMenuItem miUpdateDownloads = new JMenuItem("Liste der Downloads aktualisieren");
        miUpdateDownloads.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK));
        miUpdateDownloads.setIcon(IconFontSwing.buildIcon(FontAwesome.REFRESH, 16));
        miUpdateDownloads.addActionListener(e -> updateDownloads());
        daten.getFilmeLaden().addAdListener(new ListenerFilmeLaden() {
            @Override
            public void start(ListenerFilmeLadenEvent event) {
                miUpdateDownloads.setEnabled(false);
            }

            @Override
            public void fertig(ListenerFilmeLadenEvent event) {
                miUpdateDownloads.setEnabled(true);
            }
        });

        JMenuItem miCleanupDownloads = new JMenuItem("Liste der Downloads aufräumen");
        miCleanupDownloads.setIcon(IconFontSwing.buildIcon(FontAwesome.ERASER, 16));
        miCleanupDownloads.addActionListener(e -> cleanupDownloads());

        JMenuItem miStartDownloads = new JMenuItem("Downloads starten");
        miStartDownloads.setIcon(Icons.ICON_MENUE_DOWNOAD_STARTEN);
        miStartDownloads.addActionListener(e -> starten(false));

        JMenuItem miStopDownloads = new JMenuItem("Downloads stoppen");
        miStopDownloads.addActionListener(e -> stoppen(false));

        JMenuItem miDownloadsVorziehen = new JMenuItem("Downloads vorziehen");
        miDownloadsVorziehen.setIcon(Icons.ICON_MENUE_VORZIEHEN);
        miDownloadsVorziehen.addActionListener(e -> downloadsVorziehen());

        JMenuItem miDownloadsZurueckstellen = new JMenuItem("Downloads zurückstellen");
        miDownloadsZurueckstellen.setIcon(IconFontSwing.buildIcon(FontAwesome.CLOCK_O, 16));
        miDownloadsZurueckstellen.addActionListener(e -> downloadLoeschen(false));

        JMenuItem miDownloadsLoeschen = new JMenuItem("Downloads aus Liste entfernen");
        miDownloadsLoeschen.setIcon(IconFontSwing.buildIcon(FontAwesome.TRASH_O, 16));
        miDownloadsLoeschen.addActionListener(e -> downloadLoeschen(true));

        JMenuItem miEditDownload = new JMenuItem("Download ändern");
        miEditDownload.setIcon(IconFontSwing.buildIcon(FontAwesome.PENCIL_SQUARE_O, 16));
        miEditDownload.addActionListener(e -> editDownload());

        JMenuItem miMarkFilmAsSeen = new JMenuItem("Filme als gesehen markieren");
        miMarkFilmAsSeen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK));
        miMarkFilmAsSeen.setIcon(Icons.ICON_MENUE_HISTORY_ADD);
        miMarkFilmAsSeen.addActionListener(e -> markFilmAsSeen());

        JMenuItem miMarkFilmAsUnseen = new JMenuItem("Filme als ungesehen markieren");
        miMarkFilmAsUnseen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
        miMarkFilmAsUnseen.setIcon(Icons.ICON_MENUE_HISTORY_REMOVE);
        miMarkFilmAsUnseen.addActionListener(e -> markFilmAsUnseen());

        JMenuItem miPlayDownload = new JMenuItem("Gespeicherten Film abspielen");
        miPlayDownload.setIcon(IconFontSwing.buildIcon(FontAwesome.PLAY, 16));
        miPlayDownload.addActionListener(e -> filmAbspielen());

        JMenuItem miSearchMediaDb = new JMenuItem("Titel in der Mediensammlung suchen");
        miSearchMediaDb.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
        miSearchMediaDb.addActionListener(e -> searchInMediaDb());

        JMenuItem miInvertSelection = new JMenuItem("Auswahl umkehren");
        miInvertSelection.addActionListener(e -> tabelle.invertSelection());

        JMenuItem miShutdownAfterDownload = new JMenuItem("Rechner nach Downloads herunterfahren");
        miShutdownAfterDownload.setIcon(IconFontSwing.buildIcon(FontAwesome.POWER_OFF, 16));
        miShutdownAfterDownload.addActionListener(e -> {
            if (daten.getListeDownloads().unfinishedDownloads() > 0) {
                // ansonsten gibts keine laufenden Downloads auf die man warten sollte
                MediathekGui.ui().beenden(true, false);
            } else {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(Konstanten.PROGRAMMNAME);
                    alert.setHeaderText("Keine laufenden Downloads!");
                    alert.setContentText("Die Downloads müssen zuerst gestartet werden.");
                    alert.showAndWait();
                });
            }
        });

        JMenuItem miShowDownloadHistory = new JMenuItem("Download-Historie anzeigen...");
        miShowDownloadHistory.addActionListener(e -> showDownloadHistory());

        menu.add(miDownloadsStartAll);
        menu.add(miDownloadStartTimed);
        menu.add(miStopAllDownloads);
        menu.add(miStopWaitingDownloads);
        menu.add(miUpdateDownloads);
        menu.add(miCleanupDownloads);
        menu.addSeparator();
        menu.add(miStartDownloads);
        menu.add(miStopDownloads);
        menu.add(miDownloadsVorziehen);
        menu.add(miDownloadsZurueckstellen);
        menu.add(miDownloadsLoeschen);
        menu.add(miEditDownload);
        menu.addSeparator();
        menu.add(cbShowDownloadDescription);
        menu.addSeparator();
        menu.add(miMarkFilmAsSeen);
        menu.add(miMarkFilmAsUnseen);
        menu.add(miPlayDownload);
        menu.addSeparator();
        menu.add(miSearchMediaDb);
        menu.addSeparator();
        menu.add(miShowDownloadHistory);
        menu.addSeparator();
        menu.add(miInvertSelection);
        menu.addSeparator();
        menu.add(miShutdownAfterDownload);
    }

    class ShowDownloadHistoryDialog extends StandardCloseDialog {
        public ShowDownloadHistoryDialog(Frame owner) {
            super(owner,"Download-Historie", true);
        }

        @Override
        public JComponent createContentPanel() {
            PanelErledigteUrls panel = new PanelErledigteUrls(daten);
            panel.initHistory();
            return panel;
        }
    }

    private void showDownloadHistory() {
        ShowDownloadHistoryDialog dialog = new ShowDownloadHistoryDialog(MediathekGui.ui());
        dialog.pack();
        dialog.setVisible(true);
    }

    private void setupDescriptionPanel() {
        Platform.runLater(() -> {
            try {
                URL url = getClass().getResource("/mediathek/res/programm/fxml/filmdescription.fxml");

                FXMLLoader loader = new FXMLLoader();
                loader.setLocation(url);

                TabPane descriptionPane = loader.load();
                final DescriptionPanelController descriptionPanelController = loader.getController();
                descriptionPanelController.setOnCloseRequest(e -> {
                    SwingUtilities.invokeLater(() -> jPanelBeschreibung.setVisible(false));
                    e.consume();
                });

                JFXPanel panel = new JFXPanel();
                panel.setScene(new Scene(descriptionPane));
                tabelle.getSelectionModel().addListSelectionListener(e -> {
                    Optional<DatenFilm> optFilm = getCurrentlySelectedFilm();
                    Platform.runLater(() -> descriptionPanelController.showFilmDescription(optFilm));
                });
                SwingUtilities.invokeLater(() -> jPanelBeschreibung.add(panel, BorderLayout.CENTER));
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public void tabelleSpeichern() {
        if (tabelle != null) {
            tabelle.tabelleNachDatenSchreiben();
        }
    }

    public void onComponentShown() {
        MediathekGui.ui().tabPaneIndexProperty().setValue(TabPaneIndex.DOWNLOAD);
        updateFilmData();
    }

    public void starten(boolean alle) {
        filmStartenWiederholenStoppen(alle, true);
    }

    public void startAtTime() {
        filmStartAtTime();
    }

    public void stoppen(boolean alle) {
        filmStartenWiederholenStoppen(alle, false);
    }

    public void markFilmAsSeen() {
        daten.history.setGesehen(true, getSelFilme(), daten.getListeFilmeHistory());
    }

    public void markFilmAsUnseen() {
        daten.history.setGesehen(false, getSelFilme(), daten.getListeFilmeHistory());
    }

    private void setupKeyMappings() {
        ActionMap am = tabelle.getActionMap();
        InputMap im = tabelle.getInputMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "aendern");
        am.put("aendern", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editDownload();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "loeschen");
        am.put("loeschen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadLoeschen(true);
            }
        });

        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "tabelle");
        this.getActionMap().put("tabelle", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tabelle.requestFocusSelect(jScrollPane1);
            }
        });
        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "download");
        this.getActionMap().put("download", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filmStartenWiederholenStoppen(false, true /* starten */);
            }
        });

        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_U, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "url-copy");
        this.getActionMap().put("url-copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tabelle.getSelectedRow();
                if (row != -1) {
                    GuiFunktionen.copyToClipboard(tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row),
                            DatenDownload.DOWNLOAD_URL).toString());
                }
            }
        });

        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_M, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "searchInMediaDb");
        this.getActionMap().put("searchInMediaDb", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tabelle.getSelectedRow();
                if (row != -1) {
                    MVConfig.add(MVConfig.Configs.SYSTEM_MEDIA_DB_DIALOG_ANZEIGEN, Boolean.TRUE.toString());
                    daten.getDialogMediaDB().setVis();

                    DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
                    if (datenDownload != null) {
                        daten.getDialogMediaDB().setFilter(datenDownload.arr[DatenDownload.DOWNLOAD_TITEL]);
                    }

                }
            }
        });
        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "gesehen");
        this.getActionMap().put("gesehen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                markFilmAsSeen();
            }
        });
        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "ungesehen");
        this.getActionMap().put("ungesehen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                markFilmAsUnseen();
            }
        });
    }

    private final static int[] COLUMNS_DISABLED = new int[]{DatenDownload.DOWNLOAD_BUTTON_START,
            DatenDownload.DOWNLOAD_BUTTON_DEL,
            DatenDownload.DOWNLOAD_REF,
            DatenDownload.DOWNLOAD_URL_RTMP,
            DatenDownload.DOWNLOAD_FILM_NR,
            DatenDownload.DOWNLOAD_NR};

    private void init() {
        setupKeyMappings();
        //Tabelle einrichten

        final CellRendererDownloads cellRenderer = new CellRendererDownloads(daten.getSenderIconCache());
        tabelle.setDefaultRenderer(Object.class, cellRenderer);
        tabelle.setDefaultRenderer(Datum.class, cellRenderer);
        tabelle.setDefaultRenderer(MVFilmSize.class, cellRenderer);
        tabelle.setDefaultRenderer(Integer.class, cellRenderer);

        model = new TModelDownload(new Object[][]{}, DatenDownload.COLUMN_NAMES);
        tabelle.setModel(model);
        tabelle.addMouseListener(new BeobMausTabelle());
        tabelle.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateFilmData();
            }
        });

        tabelle.setLineBreak(MVConfig.getBool(MVConfig.Configs.SYSTEM_TAB_DOWNLOAD_LINEBREAK));
        tabelle.getTableHeader().addMouseListener(new BeobTableHeader(tabelle,
                DatenDownload.COLUMN_NAMES,
                DatenDownload.spaltenAnzeigen,
                COLUMNS_DISABLED,
                new int[]{DatenDownload.DOWNLOAD_BUTTON_START, DatenDownload.DOWNLOAD_BUTTON_DEL},
                true, MVConfig.Configs.SYSTEM_TAB_DOWNLOAD_LINEBREAK));

        btnClear.setIcon(Icons.ICON_BUTTON_CLEAR);
        btnClear.addActionListener(l -> {
            cbDisplayCategories.setSelectedIndex(0);
            cbView.setSelectedIndex(0);
        });

        jSpinnerAnzahlDownloads.setModel(new SpinnerNumberModel(1, 1, 9, 1));
        jSpinnerAnzahlDownloads.setValue(Integer.parseInt(MVConfig.get(MVConfig.Configs.SYSTEM_MAX_DOWNLOAD)));
        jSpinnerAnzahlDownloads.addChangeListener(l -> {
            MVConfig.add(MVConfig.Configs.SYSTEM_MAX_DOWNLOAD,
                    String.valueOf(((Number) jSpinnerAnzahlDownloads.getModel().getValue()).intValue()));
            Listener.notify(Listener.EREIGNIS_ANZAHL_DOWNLOADS, GuiDownloads.class.getSimpleName());
        });

        jSplitPane1.setDividerLocation(MVConfig.getInt(MVConfig.Configs.SYSTEM_PANEL_DOWNLOAD_DIVIDER));
        jSplitPane1.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, pce -> {
            if (jScrollPaneFilter.isVisible()) {
                MVConfig.add(MVConfig.Configs.SYSTEM_PANEL_DOWNLOAD_DIVIDER, String.valueOf(jSplitPane1.getDividerLocation()));
            }
        });
        jScrollPaneFilter.setVisible(MVConfig.getBool(MVConfig.Configs.SYSTEM_TAB_DOWNLOAD_FILTER_VIS));
        Listener.addListener(new Listener(Listener.EREIGNIS_PANEL_DOWNLOAD_FILTER_ANZEIGEN, GuiDownloads.class.getSimpleName()) {
            @Override
            public void ping() {
                setFilter();
            }
        });

        setTimer();
        daten.getFilmeLaden().addAdListener(new ListenerFilmeLaden() {
            @Override
            public void start(ListenerFilmeLadenEvent event) {
                loadFilmlist = true;
            }

            @Override
            public void fertig(ListenerFilmeLadenEvent event) {
                loadFilmlist = false;
                daten.getListeDownloads().filmEintragen();
                if (Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_ABOS_SOFORT_SUCHEN))) {
                    updateDownloads();
                } else {
                    reloadTable(); // damit die Filmnummern richtig angezeigt werden
                }
            }
        });
    }

    private void setFilter() {
        // Panel anzeigen und die Filmliste anpassen
        jScrollPaneFilter.setVisible(MVConfig.getBool(MVConfig.Configs.SYSTEM_TAB_DOWNLOAD_FILTER_VIS));
        if (jScrollPaneFilter.isVisible()) {
            jSplitPane1.setDividerLocation(MVConfig.getInt(MVConfig.Configs.SYSTEM_PANEL_DOWNLOAD_DIVIDER));
        }
        this.updateUI();
    }

    private void setTimer() {
        txtDownload.setText("");
        txtDownload.setEditable(false);
        txtDownload.setFocusable(false);
        txtDownload.setContentType("text/html");
        try {
            if (jScrollPaneFilter.isVisible()) {
                timerTask = new TimerTask() {

                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> setInfoText());
                    }
                };
                timer.schedule(timerTask, 0, 1_000);
            } else {
                if (timerTask != null) {
                    timerTask.cancel();
                }
                timer.purge();
            }
        } catch (IllegalStateException ex) {
            logger.debug(ex);
        }
    }

    private static final Logger logger = LogManager.getLogger(GuiDownloads.class);

    private void setInfoText() {
        int[] starts = daten.getDownloadInfos().downloadStarts;
        if (starts[0] == 0) {
            txtDownload.setText("");
            return;
        }
        final String HEAD = "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><style type=\"text/css\"> .sans { font-family: Verdana, Geneva, sans-serif; }</style></head>\n"
                + "<body>\n";
        final String END = "</body></html>";

        String info = HEAD;

        // Downloads
        info += getInfoText();
        // Größe
        if (daten.getDownloadInfos().byteAlleDownloads > 0 || daten.getDownloadInfos().byteAktDownloads > 0) {
            info += "<br />";
            info += "<span class=\"sans\"><b>Größe:</b><br />";
            if (daten.getDownloadInfos().byteAktDownloads > 0) {
                info += MVFilmSize.getGroesse(daten.getDownloadInfos().byteAktDownloads) + " von "
                        + MVFilmSize.getGroesse(daten.getDownloadInfos().byteAlleDownloads) + " MByte" + "</span>";
            } else {
                info += MVFilmSize.getGroesse(daten.getDownloadInfos().byteAlleDownloads) + " MByte" + "</span>";
            }
        }
        // Restzeit
        if (daten.getDownloadInfos().timeRestAktDownloads > 0 && daten.getDownloadInfos().timeRestAllDownloads > 0) {
            info += "<br />";
            info += "<span class=\"sans\"><b>Restzeit:</b><br />" + "laufende: "
                    + daten.getDownloadInfos().getRestzeit() + ",<br />alle: " + daten.getDownloadInfos().getGesamtRestzeit() + "</span>";
        } else if (daten.getDownloadInfos().timeRestAktDownloads > 0) {
            info += "<br />";
            info += "<span class=\"sans\"><b>Restzeit:</b><br />laufende: " + daten.getDownloadInfos().getRestzeit() + "</span>";
        } else if (daten.getDownloadInfos().timeRestAllDownloads > 0) {
            info += "<br />";
            info += "<span class=\"sans\"><b>Restzeit:</b><br />alle: " + daten.getDownloadInfos().getGesamtRestzeit() + "</span>";
        }
        // Bandbreite
        if (daten.getDownloadInfos().bandwidth > 0) {
            info += "<br />";
            info += "<span class=\"sans\"><b>Bandbreite:</b><br />";
            info += daten.getDownloadInfos().bandwidthStr + "</span>";
        }
        info += END;

        txtDownload.setText(info);
    }

    private String getInfoText() {
        String textLinks;
        // Text links: Zeilen Tabelle
        // nicht gestarted, laufen, fertig OK, fertig fehler
        int[] starts = daten.getDownloadInfos().downloadStarts;
//        if (starts[0] == 1) {
//            textLinks = "<span class=\"sans\"><b>Download:</b>1<br />";
//        } else {
        textLinks = "<span class=\"sans\"><b>Downloads:  </b>" + starts[0] + "<br />";
//        }
        boolean print = false;
        for (int ii = 1; ii < starts.length; ++ii) {
            if (starts[ii] > 0) {
                print = true;
                break;
            }
        }
        if (print) {
            textLinks += "( ";
            if (starts[4] == 1) {
                textLinks += "1 läuft";
            } else {
                textLinks += starts[4] + " laufen";
            }
            if (starts[3] == 1) {
                textLinks += ", 1 wartet";
            } else {
                textLinks += ", " + starts[3] + " warten";
            }
            if (starts[5] > 0) {
                if (starts[5] == 1) {
                    textLinks += ", 1 fertig";
                } else {
                    textLinks += ", " + starts[5] + " fertig";
                }
            }
            if (starts[6] > 0) {
                if (starts[6] == 1) {
                    textLinks += ", 1 fehlerhaft";
                } else {
                    textLinks += ", " + starts[6] + " fehlerhaft";
                }
            }
            textLinks += " )";
        }
        textLinks += "<br /></span>";
        return textLinks;
    }

    private void addListenerMediathekView() {
        //register message bus handler
        daten.getMessageBus().subscribe(this);

        Listener.addListener(new Listener(Listener.EREIGNIS_BLACKLIST_GEAENDERT, GuiDownloads.class.getSimpleName()) {
            @Override
            public void ping() {
                if (Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_ABOS_SOFORT_SUCHEN))
                        && Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_BLACKLIST_AUCH_ABO))) {
                    // nur auf Blacklist reagieren, wenn auch für Abos eingeschaltet
                    updateDownloads();
                }
            }
        });
        Listener.addListener(new Listener(new int[]{Listener.EREIGNIS_BLACKLIST_AUCH_FUER_ABOS,
                Listener.EREIGNIS_LISTE_ABOS}, GuiDownloads.class.getSimpleName()) {
            @Override
            public void ping() {
                if (Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_ABOS_SOFORT_SUCHEN))) {
                    updateDownloads();
                }
            }
        });
        Listener.addListener(new Listener(new int[]{Listener.EREIGNIS_LISTE_DOWNLOADS,
                Listener.EREIGNIS_REIHENFOLGE_DOWNLOAD, Listener.EREIGNIS_RESET_INTERRUPT}, GuiDownloads.class.getSimpleName()) {
            @Override
            public void ping() {
                reloadTable();
                daten.allesSpeichern(); // damit nichts verloren geht
            }
        });

        Listener.addListener(new Listener(new int[]{Listener.EREIGNIS_ART_DOWNLOAD_PROZENT}, GuiDownloads.class.getSimpleName()) {
            @Override
            public void ping() {
                if (lastUpdate < (new Date().getTime() - 500)) {
                    // nur alle 500ms aufrufen
                    lastUpdate = new Date().getTime();
                    daten.getListeDownloads().setModelProgress(model);
                    // ist ein Kompromiss: beim Sortieren nach Progress wird die Tabelle nicht neu sortiert!
                    //tabelle.fireTableDataChanged(true /*setSpalten*/);
                }
            }
        });

        setupShowFilmDescriptionMenuItem();
    }

    @Handler
    private void handleGeoStateChangedEvent(GeoStateChangedEvent e) {
        SwingUtilities.invokeLater(() -> {
            tabelle.fireTableDataChanged(true);
            setInfo();
        });
    }

    /**
     * Setup and show film description panel.
     * Most of the setup is done in {@link GuiDownloads} function.
     * Here we just display the panel
     */
    private void setupShowFilmDescriptionMenuItem() {
        cbShowDownloadDescription.setSelected(ApplicationConfiguration.getConfiguration().getBoolean(ApplicationConfiguration.DOWNLOAD_SHOW_DESCRIPTION, true));
        cbShowDownloadDescription.addActionListener(l -> jPanelBeschreibung.setVisible(cbShowDownloadDescription.isSelected()));
        cbShowDownloadDescription.addItemListener(e -> ApplicationConfiguration.getConfiguration().setProperty(ApplicationConfiguration.DOWNLOAD_SHOW_DESCRIPTION, cbShowDownloadDescription.isSelected()));
        jPanelBeschreibung.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                cbShowDownloadDescription.setSelected(true);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                cbShowDownloadDescription.setSelected(false);
            }
        });
    }


    /**
     * Show description panel based on settings.
     */
    private void showDescriptionPanel() {
        jPanelBeschreibung.setVisible(ApplicationConfiguration.getConfiguration().getBoolean(ApplicationConfiguration.DOWNLOAD_SHOW_DESCRIPTION, true));
    }

    private synchronized void reloadTable() {
        // nur Downloads die schon in der Liste sind werden geladen
        tabelle.getSpalten();

        daten.getListeDownloads().getModel(model, onlyAbos, onlyDownloads, onlyNotStarted, onlyStarted, onlyWaiting, onlyRun, onlyFinished);
        tabelle.setSpalten();
        updateFilmData();
        setInfo();
    }

    @Handler
    private void handleStartEvent(StartEvent msg) {
        SwingUtilities.invokeLater(this::reloadTable);
    }

    private void searchInMediaDb() {
        DatenDownload datenDownload = getSelDownload();
        MVConfig.add(MVConfig.Configs.SYSTEM_MEDIA_DB_DIALOG_ANZEIGEN, Boolean.TRUE.toString());
        daten.getDialogMediaDB().setVis();

        if (datenDownload != null) {
            daten.getDialogMediaDB().setFilter(datenDownload.arr[DatenDownload.DOWNLOAD_TITEL]);
        }
    }

    public synchronized void updateDownloads() {
        if (loadFilmlist) {
            // wird danach automatisch gemacht
            return;
        }
        // erledigte entfernen, nicht gestartete Abos entfernen und neu nach Abos suchen
        daten.getListeDownloads().abosAuffrischen();
        daten.getListeDownloads().abosSuchen(parentComponent);
        reloadTable();

        if (Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_DOWNLOAD_SOFORT_STARTEN))) {
            // und wenn gewollt auch gleich starten
            filmStartenWiederholenStoppen(true /*alle*/, true /*starten*/, false /*fertige wieder starten*/);
        }
    }

    public synchronized void cleanupDownloads() {
        // abgeschlossene Downloads werden aus der Tabelle/Liste entfernt
        // die Starts dafür werden auch gelöscht
        daten.getListeDownloads().listePutzen();
    }

    private synchronized void downloadsAufraeumen(DatenDownload datenDownload) {
        // abgeschlossene Downloads werden aus der Tabelle/Liste entfernt
        // die Starts dafür werden auch gelöscht
        daten.getListeDownloads().listePutzen(datenDownload);
    }

    private ArrayList<DatenDownload> getSelDownloads() {
        ArrayList<DatenDownload> arrayDownloads = new ArrayList<>();
        int rows[] = tabelle.getSelectedRows();
        if (rows.length > 0) {
            for (int row : rows) {
                DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
                arrayDownloads.add(datenDownload);
            }
        } else {
            new HinweisKeineAuswahl().zeigen(parentComponent);
        }
        return arrayDownloads;
    }

    private Optional<DatenFilm> getCurrentlySelectedFilm() {
        final int selectedTableRow = tabelle.getSelectedRow();

        if (selectedTableRow != -1) {
            Optional<DatenFilm> optRet;
            final DatenDownload download = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(selectedTableRow), DatenDownload.DOWNLOAD_REF);
            if (download.film == null)
                optRet = Optional.empty();
            else
                optRet = Optional.of(download.film);
            return optRet;
        } else {
            return Optional.empty();
        }
    }

    private DatenDownload getSelDownload() {
        DatenDownload datenDownload = null;
        final int row = tabelle.getSelectedRow();
        if (row != -1) {
            datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
        } else {
            new HinweisKeineAuswahl().zeigen(parentComponent);
        }
        return datenDownload;
    }

    public synchronized void editDownload() {
        DatenDownload datenDownload = getSelDownload();
        if (datenDownload == null) {
            return;
        }
        boolean gestartet = false;
        if (datenDownload.start != null) {
            if (datenDownload.start.status >= Start.STATUS_RUN) {
                gestartet = true;
            }
        }
        DatenDownload datenDownloadKopy = datenDownload.getCopy();
        DialogEditDownload dialog = new DialogEditDownload(parentComponent, true, datenDownloadKopy, gestartet);
        dialog.setVisible(true);
        if (dialog.ok) {
            datenDownload.aufMichKopieren(datenDownloadKopy);
            reloadTable();
        }
    }

    private void downloadsVorziehen() {
        ArrayList<DatenDownload> arrayDownloads = getSelDownloads();
        if (arrayDownloads.isEmpty()) {
            return;
        }
        daten.getListeDownloads().downloadsVorziehen(arrayDownloads);
    }

    private void zielordnerOeffnen() {
        DatenDownload datenDownload = getSelDownload();
        if (datenDownload == null) {
            return;
        }
        String s = datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD];
        DirOpenAction.zielordnerOeffnen(parentComponent, s);
    }

    public void filmAbspielen() {
        DatenDownload datenDownload = getSelDownload();
        if (datenDownload == null) {
            return;
        }
        String s = datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME];
        OpenPlayerAction.filmAbspielen(parentComponent, s);
    }

    private void filmLoeschen_() {
        DatenDownload datenDownload = getSelDownload();
        if (datenDownload == null) {
            return;
        }
        // Download nur löschen wenn er nicht läuft
        if (datenDownload.start != null) {
            if (datenDownload.start.status < Start.STATUS_FERTIG) {
                MVMessageDialog.showMessageDialog(parentComponent, "Download erst stoppen!", "Film löschen", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try {
            File file = new File(datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME]);
            if (!file.exists()) {
                MVMessageDialog.showMessageDialog(parentComponent, "Die Datei existiert nicht!", "Film löschen", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int ret = JOptionPane.showConfirmDialog(parentComponent,
                    datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME], "Film Löschen?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.OK_OPTION) {

                // und jetzt die Datei löschen
                logger.info(new String[]{"Datei löschen: ", file.getAbsolutePath()});
                if (!file.delete()) {
                    throw new Exception();
                }
            }
        } catch (Exception ex) {
            MVMessageDialog.showMessageDialog(parentComponent, "Konnte die Datei nicht löschen!", "Film löschen", JOptionPane.ERROR_MESSAGE);
            logger.error("Fehler beim löschen: " + datenDownload.arr[DatenDownload.DOWNLOAD_ZIEL_PFAD_DATEINAME]);
        }
    }

    /**
     *
     * @param dauerhaft false werden Downloads zurück gestellt. true löscht permanent.
     */
    public void downloadLoeschen(boolean dauerhaft) {
        try {
            ArrayList<DatenDownload> arrayDownloads = getSelDownloads();
            if (arrayDownloads.isEmpty()) {
                return;
            }

            String zeit = new SimpleDateFormat("dd.MM.yyyy").format(new Date());

            ArrayList<DatenDownload> arrayDownloadsLoeschen = new ArrayList<>();
            LinkedList<MVUsedUrl> urlAboList = new LinkedList<>();

            for (DatenDownload datenDownload : arrayDownloads) {
                if (dauerhaft) {
                    arrayDownloadsLoeschen.add(datenDownload);
                    if (datenDownload.istAbo()) {
                        // ein Abo wird zusätzlich ins Logfile geschrieben
                        urlAboList.add(new MVUsedUrl(zeit,
                                datenDownload.arr[DatenDownload.DOWNLOAD_THEMA],
                                datenDownload.arr[DatenDownload.DOWNLOAD_TITEL],
                                datenDownload.arr[DatenDownload.DOWNLOAD_HISTORY_URL]));
                    }
                } else {
                    // wenn nicht dauerhaft
                    datenDownload.zurueckstellen();
                }
            }
            if (!urlAboList.isEmpty()) {
                daten.erledigteAbos.createLineWriterThread(urlAboList);
            }
            daten.getListeDownloads().downloadLoeschen(arrayDownloadsLoeschen);
            reloadTable();
//            // ausrichten
//            tabelle.setSelRow(rows[0]);
        } catch (Exception ex) {
            Log.errorLog(451203625, ex);
        }
    }

    private void filmStartAtTime() {
        // bezieht sich immer auf "alle"
        // Film der noch keinen Starts hat wird gestartet
        // Film dessen Start schon auf fertig/fehler steht wird wieder gestartet
        // wird immer vom Benutzer aufgerufen
        ArrayList<DatenDownload> listeAllDownloads = new ArrayList<>();
        ArrayList<DatenDownload> listeUrlsDownloadsAbbrechen = new ArrayList<>();
        ArrayList<DatenDownload> listeDownloadsStarten = new ArrayList<>();
        // ==========================
        // erst mal die Liste nach der Tabelle sortieren
        if (tabelle.getRowCount() == 0) {
            return;
        }
        for (int i = 0; i < tabelle.getRowCount(); ++i) {
            // um in der Reihenfolge zu starten
            DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(i), DatenDownload.DOWNLOAD_REF);
            listeAllDownloads.add(datenDownload);
            daten.getListeDownloads().remove(datenDownload);
            daten.getListeDownloads().add(datenDownload);
        }
        // ========================
        // und jetzt abarbeiten
        for (DatenDownload download : listeAllDownloads) {
            // ==========================================
            // starten
            if (download.start != null) {
                if (download.start.status == Start.STATUS_RUN) {
                    // dann läuft er schon
                    continue;
                }
                if (download.start.status > Start.STATUS_RUN) {
                    // wenn er noch läuft gibts nix
                    // wenn er schon fertig ist, erst mal fragen vor dem erneuten Starten
                    //TODO in auto dialog umwandeln!
                    int a = JOptionPane.showConfirmDialog(parentComponent, "Film nochmal starten?  ==> " + download.arr[DatenDownload.DOWNLOAD_TITEL],
                            "Fertiger Download", JOptionPane.YES_NO_OPTION);
                    if (a != JOptionPane.YES_OPTION) {
                        // weiter mit der nächsten URL
                        continue;
                    }
                    listeUrlsDownloadsAbbrechen.add(download);
                    if (download.istAbo()) {
                        // wenn er schon feritg ist und ein Abos ist, Url auch aus dem Logfile löschen, der Film ist damit wieder auf "Anfang"
                        daten.erledigteAbos.urlAusLogfileLoeschen(download.arr[DatenDownload.DOWNLOAD_HISTORY_URL]);
                    }
                }
            }
            listeDownloadsStarten.add(download);
        }
        // ========================
        // jetzt noch die Starts stoppen
        daten.getListeDownloads().downloadAbbrechen(listeUrlsDownloadsAbbrechen);

        // und die Downloads starten oder stoppen
        //alle Downloads starten/wiederstarten
        DialogBeendenZeit dialogBeenden = new DialogBeendenZeit(MediathekGui.ui(), daten, listeDownloadsStarten);
        dialogBeenden.setVisible(true);
        if (dialogBeenden.applicationCanTerminate()) {
            // fertig und beenden
            MediathekGui.ui().beenden(false /*Dialog auf "sofort beenden" einstellen*/, dialogBeenden.isShutdownRequested());
        }

        reloadTable();
    }

    private void filmStartenWiederholenStoppen(boolean alle, boolean starten /* starten/wiederstarten oder stoppen */) {
        filmStartenWiederholenStoppen(alle, starten, true /*auch fertige wieder starten*/);
    }

    private void filmStartenWiederholenStoppen(boolean alle, boolean starten /* starten/wiederstarten oder stoppen */, boolean fertige /*auch fertige wieder starten*/) {
        // bezieht sich immer auf "alle" oder nur die markierten
        // Film der noch keinen Starts hat wird gestartet
        // Film dessen Start schon auf fertig/fehler steht wird wieder gestartet
        // bei !starten wird der Film gestoppt
        // wird immer vom Benutzer aufgerufen
        ArrayList<DatenDownload> listeDownloadsLoeschen = new ArrayList<>();
        ArrayList<DatenDownload> listeDownloadsStarten = new ArrayList<>();
        ArrayList<DatenDownload> listeDownloadsMarkiert = new ArrayList<>();

        if (tabelle.getRowCount() == 0) {
            return;
        }

        // ==========================
        // erst mal die Liste nach der Tabelle sortieren
        if (starten && alle) {
            //Liste in der Reihenfolge wie in der Tabelle sortieren
            for (int i = 0; i < tabelle.getRowCount(); ++i) {
                DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(i), DatenDownload.DOWNLOAD_REF);
                daten.getListeDownloads().remove(datenDownload);
                daten.getListeDownloads().add(datenDownload);
            }
        }

        // ==========================
        // die URLs sammeln
        if (alle) {
            for (int i = 0; i < tabelle.getRowCount(); ++i) {
                DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(i), DatenDownload.DOWNLOAD_REF);
                listeDownloadsMarkiert.add(datenDownload);
            }
        } else {
            listeDownloadsMarkiert = getSelDownloads();
        }
        if (!starten) {
            // dann das Starten von neuen Downloads etwas Pausieren
            daten.starterClass.pause();
        }
        // ========================
        // und jetzt abarbeiten
        int antwort = -1;
        for (DatenDownload download : listeDownloadsMarkiert) {
            if (starten) {
                // ==========================================
                // starten
                if (download.start != null) {
                    if (download.start.status == Start.STATUS_RUN
                            || !fertige && download.start.status > Start.STATUS_RUN) {
                        // wenn er noch läuft gibts nix
                        // fertige bleiben auch unverändert
                        continue;
                    }
                    if (download.start.status > Start.STATUS_RUN) {
                        // wenn er schon fertig ist, erst mal fragen vor dem erneuten Starten
                        //TODO in auto dialog umwandeln!
                        if (antwort == -1) {
                            // nur einmal fragen
                            String text;
                            if (listeDownloadsMarkiert.size() > 1) {
                                text = "Es sind bereits fertige Filme dabei,\n"
                                        + "diese nochmal starten?";
                            } else {
                                text = "Film nochmal starten?  ==> " + download.arr[DatenDownload.DOWNLOAD_TITEL];
                            }
                            antwort = JOptionPane.showConfirmDialog(parentComponent, text,
                                    "Fertiger Download", JOptionPane.YES_NO_CANCEL_OPTION);
                        }
                        if (antwort == JOptionPane.CANCEL_OPTION) {
                            //=============================
                            //dann wars das
                            return;
                        }
                        if (antwort == JOptionPane.NO_OPTION) {
                            // weiter mit der nächsten URL
                            continue;
                        }
                        listeDownloadsLoeschen.add(download);
                        if (download.istAbo()) {
                            // wenn er schon feritg ist und ein Abos ist, Url auch aus dem Logfile löschen, der Film ist damit wieder auf "Anfang"
                            daten.erledigteAbos.urlAusLogfileLoeschen(download.arr[DatenDownload.DOWNLOAD_HISTORY_URL]);
                        }
                    }
                }
                listeDownloadsStarten.add(download);
            } else if (download.start != null) {
                // ==========================================
                // stoppen
                // wenn kein s -> dann gibts auch nichts zum stoppen oder wieder-starten
                if (download.start.status <= Start.STATUS_RUN) {
                    // löschen -> nur wenn noch läuft, sonst gibts nichts mehr zum löschen
                    listeDownloadsLoeschen.add(download);
                }
            }
        }
        // ========================
        // jetzt noch die Starts stoppen
        daten.getListeDownloads().downloadAbbrechen(listeDownloadsLoeschen);
        // und die Downloads starten oder stoppen
        if (starten) {
            //alle Downloads starten/wiederstarten
            DatenDownload.startenDownloads(daten, listeDownloadsStarten);
        }
        reloadTable();
    }

    public void stopAllWaitingDownloads() {
        // es werden alle noch nicht gestarteten Downloads gelöscht
        ArrayList<DatenDownload> listeStopDownload = new ArrayList<>();
        for (int i = 0; i < tabelle.getRowCount(); ++i) {
            DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(i), DatenDownload.DOWNLOAD_REF);
            if (datenDownload.start != null) {
                if (datenDownload.start.status < Start.STATUS_RUN) {
                    listeStopDownload.add(datenDownload);
                }
            }
        }
        daten.getListeDownloads().downloadAbbrechen(listeStopDownload);
    }

    private void setInfo() {
        daten.getMessageBus().publishAsync(new UpdateStatusBarLeftDisplayEvent());
    }

    /**
     * Return the model used for the display categories {@link javax.swing.JComboBox}.
     *
     * @return The selection model.
     */
    private DefaultComboBoxModel<String> getDisplaySelectionModel() {
        return new DefaultComboBoxModel<>(new String[]{COMBO_DISPLAY_ALL, COMBO_DISPLAY_DOWNLOADS_ONLY, COMBO_DISPLAY_ABOS_ONLY});
    }

    private DefaultComboBoxModel<String> getViewModel() {
        return new DefaultComboBoxModel<>(new String[]{COMBO_VIEW_ALL, COMBO_VIEW_NOT_STARTED, COMBO_VIEW_STARTED, COMBO_VIEW_WAITING, COMBO_VIEW_RUN_ONLY, COMBO_VIEW_FINISHED_ONLY});
    }

    private void updateFilmData() {
        if (isShowing()) {
            DatenFilm aktFilm = null;
            final int selectedTableRow = tabelle.getSelectedRow();
            if (selectedTableRow >= 0) {
                final DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(selectedTableRow), DatenDownload.DOWNLOAD_REF);
                if (datenDownload != null) {
                    aktFilm = datenDownload.film;
                }
            }
            MediathekGui.ui().getFilmInfoDialog().updateCurrentFilm(aktFilm);
        }
    }

    private ArrayList<DatenFilm> getSelFilme() {
        ArrayList<DatenFilm> arrayFilme = new ArrayList<>();
        int rows[] = tabelle.getSelectedRows();
        if (rows.length > 0) {
            for (int row : rows) {
                DatenDownload datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
                if (datenDownload.film != null) {
                    arrayFilme.add(datenDownload.film);
                }
            }
        } else {
            new HinweisKeineAuswahl().zeigen(parentComponent);
        }
        return arrayFilme;
    }

    public class BeobMausTabelle extends MouseAdapter {

        private Point p;
        DatenDownload datenDownload = null;

        @Override
        public void mouseClicked(MouseEvent arg0) {
            if (arg0.getButton() == MouseEvent.BUTTON1) {
                if (arg0.getClickCount() == 1) {
                    p = arg0.getPoint();
                    int row = tabelle.rowAtPoint(p);
                    int column = tabelle.columnAtPoint(p);
                    if (row >= 0) {
                        buttonTable(row, column);
                    }
                } else if (arg0.getClickCount() > 1) {
                    editDownload();
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent arg0) {
            p = arg0.getPoint();
            int row = tabelle.rowAtPoint(p);
            if (row >= 0) {
                datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
            }
            if (arg0.isPopupTrigger()) {
                showMenu(arg0);
            }
        }

        @Override
        public void mouseReleased(MouseEvent arg0) {
            p = arg0.getPoint();
            int row = tabelle.rowAtPoint(p);
            if (row >= 0) {
                datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
            }
            if (arg0.isPopupTrigger()) {
                showMenu(arg0);
            }
        }

        private void buttonTable(int row, int column) {
            if (row != -1) {
                datenDownload = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
                if (tabelle.convertColumnIndexToModel(column) == DatenDownload.DOWNLOAD_BUTTON_START) {
                    // filmStartenWiederholenStoppen(boolean alle, boolean starten /* starten/wiederstarten oder stoppen */)
                    if (datenDownload.start != null && !datenDownload.isDownloadManager()) {
                        if (datenDownload.start.status == Start.STATUS_FERTIG) {
                            filmAbspielen();
                        } else if (datenDownload.start.status == Start.STATUS_ERR) {
                            // Download starten
                            filmStartenWiederholenStoppen(false, true /*starten*/);
                        } else {
                            // Download stoppen
                            filmStartenWiederholenStoppen(false, false /*starten*/);
                        }
                    } else {
                        // Download starten
                        filmStartenWiederholenStoppen(false, true /*starten*/);
                    }
                } else if (tabelle.convertColumnIndexToModel(column) == DatenDownload.DOWNLOAD_BUTTON_DEL) {
                    if (datenDownload.start != null) {
                        if (datenDownload.start.status >= Start.STATUS_FERTIG) {
                            downloadsAufraeumen(datenDownload);
                        } else {
                            // Download dauerhaft löschen
                            downloadLoeschen(true);
                        }
                    } else {
                        // Download dauerhaft löschen
                        downloadLoeschen(true);
                    }
                }
            }
        }

        private void showMenu(MouseEvent evt) {
            p = evt.getPoint();
            final int nr = tabelle.rowAtPoint(p);
            if (nr != -1) {
                tabelle.setRowSelectionInterval(nr, nr);
            }
            JPopupMenu jPopupMenu = new JPopupMenu();

            //Film vorziehen
            boolean wartenOderLaufen = false;
            final int row = tabelle.getSelectedRow();
            if (row != -1) {
                DatenDownload download = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(row), DatenDownload.DOWNLOAD_REF);
                if (download.start != null) {
                    if (download.start.status <= Start.STATUS_RUN) {
                        wartenOderLaufen = true;
                    }
                }
            }
            // Download starten
            JMenuItem itemStarten = new JMenuItem("Download starten");
            itemStarten.setIcon(Icons.ICON_MENUE_DOWNOAD_STARTEN);
            itemStarten.setEnabled(!wartenOderLaufen);
            jPopupMenu.add(itemStarten);
            itemStarten.addActionListener(arg0 -> filmStartenWiederholenStoppen(false /* alle */, true /* starten */));

            // Download stoppen
            JMenuItem itemStoppen = new JMenuItem("Download stoppen");
            itemStoppen.setEnabled(wartenOderLaufen);
            jPopupMenu.add(itemStoppen);
            itemStoppen.addActionListener(arg0 -> filmStartenWiederholenStoppen(false /* alle */, false /* starten */));

            jPopupMenu.addSeparator();

            JMenuItem itemVorziehen = new JMenuItem("Download vorziehen");
            itemVorziehen.setIcon(Icons.ICON_MENUE_VORZIEHEN);
            jPopupMenu.add(itemVorziehen);
            itemVorziehen.addActionListener(arg0 -> downloadsVorziehen());

            JMenuItem itemLoeschen = new JMenuItem("Download zurückstellen");
            itemLoeschen.setIcon(IconFontSwing.buildIcon(FontAwesome.CLOCK_O, 16));
            jPopupMenu.add(itemLoeschen);
            itemLoeschen.addActionListener(arg0 -> downloadLoeschen(false /* dauerhaft */));
            //dauerhaft löschen
            JMenuItem itemDauerhaftLoeschen = new JMenuItem("Download aus Liste entfernen");
            itemDauerhaftLoeschen.setIcon(IconFontSwing.buildIcon(FontAwesome.TRASH_O, 16));
            jPopupMenu.add(itemDauerhaftLoeschen);
            itemDauerhaftLoeschen.addActionListener(arg0 -> downloadLoeschen(true /* dauerhaft */));
            //Download ändern
            JMenuItem itemAendern = new JMenuItem("Download ändern");
            itemAendern.setIcon(IconFontSwing.buildIcon(FontAwesome.PENCIL_SQUARE_O, 16));
            jPopupMenu.add(itemAendern);
            itemAendern.addActionListener(arg0 -> editDownload());

            jPopupMenu.addSeparator();

            JMenuItem itemAlleStarten = new JMenuItem("alle Downloads starten");
            itemAlleStarten.setIcon(IconFontSwing.buildIcon(FontAwesome.ANGLE_DOUBLE_DOWN, 16));
            jPopupMenu.add(itemAlleStarten);
            itemAlleStarten.addActionListener(arg0 -> filmStartenWiederholenStoppen(true /* alle */, true /* starten */));
            JMenuItem itemAlleStoppen = new JMenuItem("alle Downloads stoppen");
            jPopupMenu.add(itemAlleStoppen);
            itemAlleStoppen.addActionListener(arg0 -> filmStartenWiederholenStoppen(true /* alle */, false /* starten */));

            JMenuItem itemWartendeStoppen = new JMenuItem("wartende Downloads stoppen");
            jPopupMenu.add(itemWartendeStoppen);
            itemWartendeStoppen.addActionListener(arg0 -> stopAllWaitingDownloads());

            JMenuItem itemAktualisieren = new JMenuItem("Liste der Downloads aktualisieren");
            itemAktualisieren.setIcon(IconFontSwing.buildIcon(FontAwesome.REFRESH, 16));
            jPopupMenu.add(itemAktualisieren);
            itemAktualisieren.addActionListener(arg0 -> updateDownloads());

            JMenuItem itemAufraeumen = new JMenuItem("Liste der Downloads aufräumen");
            itemAufraeumen.setIcon(IconFontSwing.buildIcon(FontAwesome.ERASER, 16));
            jPopupMenu.add(itemAufraeumen);
            itemAufraeumen.addActionListener(arg0 -> cleanupDownloads());

            jPopupMenu.addSeparator();

            // Film abspielen
            JMenuItem itemPlayerDownload = new JMenuItem("gespeicherten Film (Datei) abspielen");
            itemPlayerDownload.setIcon(IconFontSwing.buildIcon(FontAwesome.PLAY, 16));

            itemPlayerDownload.addActionListener(e -> filmAbspielen());
            jPopupMenu.add(itemPlayerDownload);
            // Film löschen
            JMenuItem itemDeleteDownload = new JMenuItem("gespeicherten Film (Datei) löschen");
            itemDeleteDownload.setIcon(Icons.ICON_MENUE_DOWNOAD_LOESCHEN);

            itemDeleteDownload.addActionListener(e -> filmLoeschen_());
            jPopupMenu.add(itemDeleteDownload);
            // Zielordner öffnen
            JMenuItem itemOeffnen = new JMenuItem("Zielordner öffnen");
            itemOeffnen.setIcon(Icons.ICON_MENUE_FILE_OPEN);
            jPopupMenu.add(itemOeffnen);
            itemOeffnen.addActionListener(e -> zielordnerOeffnen());

            //#######################################
            jPopupMenu.addSeparator();
            //#######################################

            //Abo ändern
            JMenu submenueAbo = new JMenu("Abo");
            JMenuItem itemChangeAbo = new JMenuItem("Abo ändern");
            JMenuItem itemDelAbo = new JMenuItem("Abo löschen");
            if (datenDownload == null) {
                submenueAbo.setEnabled(false);
                itemChangeAbo.setEnabled(false);
                itemDelAbo.setEnabled(false);
            } else if (datenDownload.film == null) {
                submenueAbo.setEnabled(false);
                itemChangeAbo.setEnabled(false);
                itemDelAbo.setEnabled(false);
            } else {
                final DatenAbo datenAbo = daten.getListeAbo().getAboFuerFilm_schnell(datenDownload.film, false /*die Länge nicht prüfen*/);
                if (datenAbo == null) {
                    submenueAbo.setEnabled(false);
                    itemChangeAbo.setEnabled(false);
                    itemDelAbo.setEnabled(false);
                } else {
                    // dann können wir auch ändern
                    itemDelAbo.addActionListener(e -> daten.getListeAbo().aboLoeschen(datenAbo));
                    itemChangeAbo.addActionListener(e -> {
                        DialogEditAbo dialog = new DialogEditAbo(MediathekGui.ui(), true, daten, datenAbo, false/*onlyOne*/);
                        dialog.setVisible(true);
                        if (dialog.ok) {
                            daten.getListeAbo().aenderungMelden();
                        }
                    });
                }
            }
            submenueAbo.add(itemDelAbo);
            submenueAbo.add(itemChangeAbo);
            jPopupMenu.add(submenueAbo);

            //#######################################
            jPopupMenu.addSeparator();
            //#######################################

            // Film in der MediaDB suchen
            JMenuItem itemDb = new JMenuItem("Titel in der Mediensammlung suchen");
            itemDb.addActionListener(e -> searchInMediaDb());
            jPopupMenu.add(itemDb);

            // URL abspielen
            JMenuItem itemPlayer = new JMenuItem("Film (URL) abspielen");
            itemPlayer.addActionListener(e -> {
                int nr1 = tabelle.rowAtPoint(p);
                if (nr1 != -1) {
                    DatenPset gruppe = Daten.listePset.getPsetAbspielen();
                    if (gruppe != null) {
                        DatenDownload datenDownload1 = (DatenDownload) tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(nr1), DatenDownload.DOWNLOAD_REF);
                        if (datenDownload1 != null) {
                            if (datenDownload1.film != null) {
                                DatenFilm filmDownload = datenDownload1.film.getCopy();
                                // und jetzt die tatsächlichen URLs des Downloads eintragen
                                filmDownload.arr[DatenFilm.FILM_URL] = datenDownload1.arr[DatenDownload.DOWNLOAD_URL];
                                filmDownload.arr[DatenFilm.FILM_URL_KLEIN] = "";
                                // und starten
                                daten.starterClass.urlMitProgrammStarten(gruppe, filmDownload, "" /*Auflösung*/);
                            }
                        }
                    } else {
                        String menuPath;
                        if (SystemUtils.IS_OS_MAC_OSX) {
                            menuPath = "MediathekView->Einstellungen…->Aufzeichnen und Abspielen->Set bearbeiten";
                        } else {
                            menuPath = "Datei->Einstellungen->Set bearbeiten";
                        }
                        MVMessageDialog.showMessageDialog(parentComponent, "Bitte legen Sie im Menü \"" + menuPath + "\" ein Programm zum Abspielen fest.",
                                "Kein Videoplayer!", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            });
            jPopupMenu.add(itemPlayer);

            // URL kopieren
            JMenuItem itemUrl = new JMenuItem("URL kopieren");
            KeyStroke ctrlU = KeyStroke.getKeyStroke(KeyEvent.VK_U, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
            itemUrl.setAccelerator(ctrlU);
            itemUrl.addActionListener(e -> {
                int nr1 = tabelle.rowAtPoint(p);
                if (nr1 != -1) {
                    GuiFunktionen.copyToClipboard(
                            tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(nr1),
                                    DatenDownload.DOWNLOAD_URL).toString());
                }
            });
            jPopupMenu.add(itemUrl);

            jPopupMenu.add(showFilmInformationAction);

            jPopupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
        }
        private final ShowFilmInformationAction showFilmInformationAction = new ShowFilmInformationAction();
    }

    /**
     * This class filters the shown table items based on the made selection.
     */
    private class DisplayCategoryListener implements ActionListener {

        @Override
        @SuppressWarnings("unchecked")
        public void actionPerformed(ActionEvent e) {
            JComboBox<String> box = (JComboBox<String>) e.getSource();
            final String action = (String) box.getSelectedItem();

            assert action != null;
            switch (action) {
                case COMBO_DISPLAY_ALL:
                    onlyAbos = false;
                    onlyDownloads = false;
                    break;
                case COMBO_DISPLAY_DOWNLOADS_ONLY:
                    onlyAbos = false;
                    onlyDownloads = true;
                    break;
                case COMBO_DISPLAY_ABOS_ONLY:
                    onlyAbos = true;
                    onlyDownloads = false;
                    break;

                case COMBO_VIEW_ALL:
                    onlyNotStarted = false;
                    onlyStarted = false;
                    onlyWaiting = false;
                    onlyFinished = false;
                    onlyRun = false;
                    break;
                case COMBO_VIEW_NOT_STARTED:
                    onlyNotStarted = true;
                    onlyStarted = false;
                    onlyWaiting = false;
                    onlyFinished = false;
                    onlyRun = false;
                    break;
                case COMBO_VIEW_STARTED:
                    onlyNotStarted = false;
                    onlyStarted = true;
                    onlyWaiting = false;
                    onlyFinished = false;
                    onlyRun = false;
                    break;
                case COMBO_VIEW_WAITING:
                    onlyNotStarted = false;
                    onlyStarted = false;
                    onlyWaiting = true;
                    onlyFinished = false;
                    onlyRun = false;
                    break;
                case COMBO_VIEW_FINISHED_ONLY:
                    onlyNotStarted = false;
                    onlyStarted = false;
                    onlyWaiting = false;
                    onlyFinished = true;
                    onlyRun = false;
                    break;
                case COMBO_VIEW_RUN_ONLY:
                    onlyNotStarted = false;
                    onlyStarted = false;
                    onlyWaiting = false;
                    onlyFinished = false;
                    onlyRun = true;
                    break;
            }

            reloadTable();
        }
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JPanel jPanel2 = new javax.swing.JPanel();
        javax.swing.JScrollPane jScrollPane2 = new javax.swing.JScrollPane();
        javax.swing.JEditorPane jEditorPane1 = new javax.swing.JEditorPane();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPaneFilter = new javax.swing.JScrollPane();
        javax.swing.JPanel jPanelFilterExtern = new javax.swing.JPanel();
        javax.swing.JLabel lblAnzeigen = new javax.swing.JLabel();
        cbDisplayCategories = new javax.swing.JComboBox<>();
        cbView = new javax.swing.JComboBox<>();
        btnClear = new javax.swing.JButton();
        javax.swing.JSeparator jSeparator1 = new javax.swing.JSeparator();
        javax.swing.JScrollPane spDownload = new javax.swing.JScrollPane();
        txtDownload = new javax.swing.JEditorPane();
        javax.swing.JPanel jPanel4 = new javax.swing.JPanel();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        jSpinnerAnzahlDownloads = new javax.swing.JSpinner();
        javax.swing.JPanel jPanel5 = new javax.swing.JPanel();
        javax.swing.JPanel jPanel3 = new javax.swing.JPanel();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        jSpinner1 = new javax.swing.JSpinner();
        javax.swing.JLabel lblBandwidth = new javax.swing.JLabel();
        javax.swing.JPanel jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        javax.swing.JTable jTable1 = new javax.swing.JTable();
        jPanelBeschreibung = new javax.swing.JPanel();

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(jEditorPane1);

        setLayout(new java.awt.BorderLayout());

        jSplitPane1.setDividerLocation(400);

        jPanelFilterExtern.setPreferredSize(new java.awt.Dimension(200, 644));

        lblAnzeigen.setText("Anzeigen:");

        cbView.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        btnClear.setIcon(new javax.swing.ImageIcon(getClass().getResource("/mediathek/res/muster/button-clear.png"))); // NOI18N
        btnClear.setToolTipText("Alles löschen");

        txtDownload.setEditable(false);
        txtDownload.setOpaque(false);
        txtDownload.setPreferredSize(new java.awt.Dimension(10, 21));
        spDownload.setViewportView(txtDownload);

        jLabel3.setText("gleichzeitige Downloads:");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSpinnerAnzahlDownloads, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jSpinnerAnzahlDownloads, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jLabel1.setText("KiB/s");

        jSpinner1.setModel(new javax.swing.SpinnerNumberModel(0, 0, 1048576, 1));
        jSpinner1.setToolTipText("<html>\nBandbreitenbegrenzung eines Downloads in XX Kilobytes pro Sekunde.\n<b><br><u>WICHTIG:</u><br>ENTWEDER<br>den Wert über die Pfeiltasten ändern<br>ODER<br>Zahlen eingeben UND ENTER-Taste drücken!</b>\n</html>");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addContainerGap())
        );

        lblBandwidth.setText("max. Bandbreite je Download:");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(lblBandwidth)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 189, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblBandwidth)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelFilterExternLayout = new javax.swing.GroupLayout(jPanelFilterExtern);
        jPanelFilterExtern.setLayout(jPanelFilterExternLayout);
        jPanelFilterExternLayout.setHorizontalGroup(
            jPanelFilterExternLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelFilterExternLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelFilterExternLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cbDisplayCategories, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cbView, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelFilterExternLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnClear))
                    .addComponent(jSeparator1)
                    .addComponent(spDownload, javax.swing.GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelFilterExternLayout.createSequentialGroup()
                        .addComponent(lblAnzeigen)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelFilterExternLayout.setVerticalGroup(
            jPanelFilterExternLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelFilterExternLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblAnzeigen)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbDisplayCategories, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbView, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnClear)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(36, 36, 36)
                .addComponent(spDownload, javax.swing.GroupLayout.DEFAULT_SIZE, 346, Short.MAX_VALUE)
                .addContainerGap())
        );

        jScrollPaneFilter.setViewportView(jPanelFilterExtern);

        jSplitPane1.setLeftComponent(jScrollPaneFilter);

        jPanel1.setLayout(new java.awt.BorderLayout());

        jTable1.setAutoCreateRowSorter(true);
        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(jTable1);

        jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        jPanelBeschreibung.setLayout(new java.awt.BorderLayout());
        jPanel1.add(jPanelBeschreibung, java.awt.BorderLayout.SOUTH);

        jSplitPane1.setRightComponent(jPanel1);

        add(jSplitPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnClear;
    private javax.swing.JComboBox<String> cbDisplayCategories;
    private javax.swing.JComboBox<String> cbView;
    private javax.swing.JPanel jPanelBeschreibung;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPaneFilter;
    private javax.swing.JSpinner jSpinner1;
    private javax.swing.JSpinner jSpinnerAnzahlDownloads;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JEditorPane txtDownload;
    // End of variables declaration//GEN-END:variables
}
