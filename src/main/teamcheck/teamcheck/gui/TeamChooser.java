package teamcheck.gui;

import module chariot;
import module java.base;
import module java.desktop;

import java.util.List;
import javax.swing.GroupLayout;

import teamcheck.CheckUtil;

public class TeamChooser extends JFrame {

    Client client = null;

    final Preferences prefs;
    final JPanel mainPanel;
    final JPanel boidsPanel;

    ExecutorService executor = Executors.newCachedThreadPool();
    List<TeamNameAndId> leaderTeams = Collections.synchronizedList(new ArrayList<>());

    Font mono = Font.decode(Font.MONOSPACED + "-BOLD-30");

    JButton loginButton;
    JButton logoutButton;
    JButton launchButton;

    JTextField searchField;
    JComboBox<TeamNameAndId> combo;
    JLabel selectTeamLabel;

    record TeamNameAndId(String name, String id) {
        @Override public String toString() { return name(); }
    }

    public TeamChooser(Client client, Preferences prefs) {
        super("Team Check");

        this.client = client;
        this.prefs = prefs;

        var screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        var windowSize = new Dimension((int) (screenSize.width  * 0.8f), (int) (screenSize.height * 0.8f));
        setPreferredSize(windowSize);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);

        mainPanel = createMainPanel();

        boidsPanel = createBoidsPanel(mainPanel);

        getContentPane().add(mainPanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void launch(String teamId, Consumer<String> updateTitle) {
        var boids = new Boids();
        boids.setOpaque(false);
        boidsPanel.add(boids, BorderLayout.CENTER);

        getContentPane().remove(mainPanel);
        getContentPane().add(boidsPanel);

        pack();
        repaint();

        executor.submit( () -> {
            var teamF = new CompletableFuture<Team>();
            CheckUtil teamCheck = CheckUtil.of(
                    teamId,
                    t -> teamF.complete(t),
                    boids::spawnBoidOnEdge
                    );

            if (client instanceof ClientAuth clientAuth) {
                var mate = teamCheck.andMate(clientAuth, () -> {
                    // onExpiredToken
                    clientAuth.clearAuth(prefs);
                    client = Client.load(prefs);
                    boids.deweaponize();

                    SwingUtilities.invokeLater(() -> {
                        loginButton.setEnabled(true);
                        logoutButton.setEnabled(false);
                        combo.removeAllItems();
                        combo.setEnabled(false);
                    });

                });

                boids.weaponize(mate);
            }

            try {
                var future = executor.submit(() -> teamCheck.process(client));
                var team = teamF.get(10, TimeUnit.SECONDS);

                updateTitle.accept(team.name());

                executor.submit(() -> boids.start());
                future.get();
            } catch (Exception e) {}

        });
    }

    private JPanel createMainPanel() {

        combo = new JComboBox<>();
        JPanel panel = new CheckeredPanel();
        var layout = new GroupLayout(panel);
        panel.setLayout(layout);

        loginButton = new JButton("Login...");
        initComponent(loginButton);

        logoutButton = new JButton("Logout");
        initComponent(logoutButton);
        logoutButton.setEnabled(false);
        logoutButton.addActionListener(_ -> {

            SwingUtilities.invokeLater(() -> {
                loginButton.setEnabled(true);
                logoutButton.setEnabled(false);
                combo.removeAllItems();
                combo.setEnabled(false);
            });

            if (client instanceof ClientAuth clientAuth) {
                clientAuth.clearAuth(prefs);
                clientAuth.revokeToken();

                // Reload client with non-auth info
                client = Client.load(prefs);
            }

        });

        JLabel searchLabel = new JLabel("Team search:");
        initComponent(searchLabel);

        searchField = new JTextField();
        searchField.setOpaque(false);
        searchField.setFont(mono.deriveFont(Font.PLAIN));
        searchField.setColumns(20);

        initComponent(combo);
        combo.setBackground(Color.WHITE);
        combo.setEditable(false);
        combo.setPrototypeDisplayValue(new TeamNameAndId("A Placeholder Name For Long Enough", "placeholder"));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JComponent result = (JComponent)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                result.setOpaque(false);
                return result;
            }
        });

        combo.setFont(mono.deriveFont(Font.PLAIN));
        combo.setMaximumSize(combo.getPreferredSize());
        combo.setFocusable(true);
        combo.addItemListener(_ -> {
            if (combo.isEnabled()) {
                if (combo.getSelectedItem() instanceof TeamNameAndId team) {
                    prefs.put("selectedTeamName", team.name());
                    prefs.put("selectedTeamId", team.id());
                } else {
                    prefs.remove("selectedTeamName");
                    prefs.remove("selectedTeamId");
                }
                try { prefs.flush(); } catch (Exception e) {}
            }
        });
        searchField.setMaximumSize(combo.getPreferredSize());

        selectTeamLabel = new JLabel("Selected:");
        initComponent(selectTeamLabel);
        selectTeamLabel.setEnabled(false);

        launchButton = new JButton("Launch...");
        initComponent(launchButton);
        launchButton.addActionListener(_ -> {
            TeamNameAndId team = (TeamNameAndId) combo.getSelectedItem();
            if (team != null) {
                searchField.setText("");
                launch(team.id(), s -> SwingUtilities.invokeLater(() -> setTitle("Team Check - " + s)));
            }
        });

        if (client instanceof ClientAuth) {
            loginButton.setEnabled(false);
            logoutButton.setEnabled(true);

            executor.submit(() -> {
                teamsFromClientToPrefs();
                teamsFromPrefsToCombo();
            });

        } else {
            loginButton.setEnabled(true);
            logoutButton.setEnabled(false);
        }

        JButton aboutButton = new JButton("About...");
        initComponent(aboutButton);
        aboutButton.addActionListener(_ -> {
            var aboutPanel = createAboutPanel(mainPanel);

            SwingUtilities.invokeLater(() -> {
                getContentPane().remove(mainPanel);
                getContentPane().add(aboutPanel);
                pack();
                repaint();
            });
        });

        JButton exitButton = new JButton("Exit");
        initComponent(exitButton);
        exitButton.addActionListener(_ -> System.exit(0));

        loginButton.addActionListener(_ -> {
            var loginPanel = createLoginPanel(mainPanel);

            SwingUtilities.invokeLater(() -> {
                getContentPane().remove(mainPanel);
                getContentPane().add(loginPanel);
                pack();
                repaint();
            });
        });

        searchField.addActionListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                var text = searchField.getText();
                if (text.isEmpty()) {
                    boolean enabled = ! leaderTeams.isEmpty();
                    launchButton.setEnabled(false);
                    combo.removeAllItems();
                    combo.actionPerformed(null);
                    combo.setEnabled(false);
                    leaderTeams.stream().forEach(combo::addItem);
                    launchButton.setEnabled(enabled);
                    combo.setEnabled(enabled);
                    selectTeamLabel.setEnabled(enabled);
                } else {
                    launchButton.setEnabled(false);
                    combo.removeAllItems();
                    executor.submit( () -> {
                        client.teams().search(text).stream()
                            .map(t -> new TeamNameAndId(t.name(), t.id()))
                            .limit(30)
                            .filter(t ->
                                    t.name().toLowerCase().contains(text.toLowerCase()) ||
                                    t.id().toLowerCase().contains(text.toLowerCase()))
                            .forEach(t -> {
                                var item = new TeamNameAndId(t.name(), t.id());
                                SwingUtilities.invokeLater(() -> {
                                    combo.setEnabled(true);
                                    combo.addItem(item);
                                    launchButton.setEnabled(true);
                                    selectTeamLabel.setEnabled(true);
                                    repaint();
                                });
                            });
                    });
                }
            }
            );
        });


        var sid = prefs.get("selectedTeamId", null);
        var sname = prefs.get("selectedTeamName", null);

        if (sid != null && sname != null) {
            combo.setEnabled(true);
            var team = new TeamNameAndId(sname, sid);
            combo.addItem(team);
            combo.setSelectedItem(team);
            selectTeamLabel.setEnabled(true);
        }

        if (combo.getItemCount() == 0) {
            combo.setEnabled(false);
        } else {
            combo.setEnabled(true);
        }
        launchButton.setEnabled(combo.getSelectedItem() != null);



        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*2.5)).addGroup(
                    layout.createParallelGroup()
                    .addComponent(loginButton)
                    .addComponent(logoutButton)
                    .addComponent(searchLabel)
                    .addComponent(selectTeamLabel)
                    .addComponent(launchButton)
                    .addComponent(aboutButton)
                    .addComponent(exitButton)).addGroup(
                    layout.createParallelGroup()
                    .addComponent(searchField)
                    .addComponent(combo)
                    )
                );

        layout.setVerticalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*2.5))
                .addComponent(loginButton)
                .addComponent(logoutButton)
                .addGroup(
                    layout.createParallelGroup()
                    .addComponent(searchLabel)
                    .addComponent(searchField)
                    )
                .addGroup(
                    layout.createParallelGroup()
                    .addComponent(selectTeamLabel)
                    .addComponent(combo)
                    )
                .addGap(30)
                .addComponent(launchButton)
                .addGap(30)
                .addComponent(aboutButton)
                .addComponent(exitButton)
                );

        return panel;
    }


    private void teamsFromPrefsToCombo() {

        var teamsFromPrefs = new ArrayList<TeamNameAndId>();
        String selectedTeamId = prefs.get("selectedTeamId", null);

        int numTeams = prefs.getInt("numTeams", 0);
        for (int i = 0; i < numTeams; i++) {
            var team = new TeamNameAndId(prefs.get("nameTeam"+i, null), prefs.get("idTeam"+i, null));
            if (team.name() != null && team.id() != null) {
                teamsFromPrefs.add(team);
            }
        }

        SwingUtilities.invokeLater(() -> {
            combo.removeAllItems();
            for (var team : teamsFromPrefs) {
                combo.addItem(team);
                combo.setEnabled(true);
                selectTeamLabel.setEnabled(true);
                if (team.id().equals(selectedTeamId)) {
                    combo.setSelectedItem(team);
                }
            }

            searchField.setText("");
            launchButton.setEnabled(combo.getSelectedItem() != null);
        });

    }

    private void teamsFromClientToPrefs() {

        String selectedTeamId = prefs.get("selectedTeamId", null);

        if (client instanceof ClientAuth clientAuth) {
            clientAuth.account().profile().ifPresent( user -> {
                var teams = clientAuth.teams().byUserId(user.id()).stream()
                    .filter(team -> team.leaders().stream().anyMatch(leader -> leader.id().equals(user.id())))
                    .toList();
                leaderTeams.clear();
                teams.forEach(t -> leaderTeams.add(new TeamNameAndId(t.name(), t.id())));
            });
        }

        try {
            var keys = prefs.keys();
            for (String key : keys) {
                if (key.startsWith("nameTeam") ||
                    key.startsWith("idTeam") ||
                    key.startsWith("selctedTeam")) {
                    prefs.remove(key);
                }
            }

            prefs.putInt("numTeams", leaderTeams.size());

            for (int i = 0; i <  leaderTeams.size(); i++) {
                var team = leaderTeams.get(i);
                prefs.put("nameTeam" + i, team.name());
                prefs.put("idTeam" + i, team.id());
                if (team.id().equals(selectedTeamId)) {
                    prefs.put("selectedTeamId", team.id());
                }
            }

            prefs.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JPanel createBoidsPanel(JPanel main) {
        var panel = new CheckeredPanel();

        var buttonPanel = new JPanel();
        initComponent(buttonPanel);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        var backButton = new JButton("Back");
        initComponent(backButton);
        backButton.setForeground(Color.BLACK);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        buttonPanel.add(backButton);

        backButton.addActionListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                var boids = Arrays.stream(panel.getComponents())
                    .filter(c -> c instanceof Boids)
                    .map(b -> (Boids) b)
                    .findFirst();

                boids.ifPresent(b -> {
                    b.stop();
                    panel.remove(b);
                });

                getContentPane().remove(panel);
                getContentPane().add(main);
                setTitle("Team Check");
                repaint();
            });
        });

        panel.setLayout(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createLoginPanel(JPanel main) {
        var panel = new CheckeredPanel();

        var buttonPanel = new JPanel();
        initComponent(buttonPanel);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        var backButton = new JButton("Back");
        initComponent(backButton);
        backButton.setForeground(Color.BLACK);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        buttonPanel.add(backButton);

        backButton.addActionListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                getContentPane().remove(panel);
                getContentPane().add(main);
                pack();
                repaint();
            });
        });

        panel.setLayout(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        panel.add(center, BorderLayout.CENTER);

        var layout = new GroupLayout(center);
        center.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        var text = new JTextArea();
        text.setOpaque(false);
        text.setText("""
                If you are a team leader,
                you can choose to login to Lichess with this application.
                This will allow you to:

                    - Select a team among the teams you lead, without searching
                    - Kick a member by clicking on their name

                If you are not interested in those things, you do not need to login.
                """);
        text.setFont(mono.deriveFont(Font.PLAIN, 20));
        text.setEnabled(false);
        text.setDisabledTextColor(Color.BLACK);


        var oauthButton = new JButton("Open link in System Browser");
        var oauthUrlArea = new JTextArea();

        executor.submit(() -> {
                var authResult = client.withPkce(
                        uri -> {
                            oauthUrlArea.setText(uri.toString());
                            oauthButton.addActionListener(_ -> {
                                try {
                                    Desktop.getDesktop().browse(uri);
                                } catch (Exception e) {
                                    // Show message
                                    e.printStackTrace();
                                }
                            });
                        },
                        pkce -> pkce.scope(Client.Scope.team_lead, Client.Scope.team_read));

                if (authResult instanceof Client.AuthOk ok) {

                    client = ok.client();
                    client.store(prefs);

                    teamsFromClientToPrefs();
                    teamsFromPrefsToCombo();

                    SwingUtilities.invokeLater(() -> {

                        loginButton.setEnabled(false);
                        logoutButton.setEnabled(true);

                        getContentPane().remove(panel);
                        getContentPane().add(mainPanel);
                        repaint();
                    });
                } else {
                    // Some non-success,
                    // maybe timeout or didn't grant
                    System.out.println("Unsuccesful login: " + authResult);
                    backButton.doClick();
                }
        });

        boolean canOpenSystemBrowser = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);

        var oauthTextArea = new JTextArea();
        oauthTextArea.setOpaque(false);

        String oauthText = canOpenSystemBrowser ?
                        """
                        Click the button or manually copy and visit the following Lichess link,
                        where you will be asked by Lichess if you want to grant this application access:""" :
                        """
                        Visit the following Lichess link,
                        where you will be asked by Lichess if you want to grant this application access:""";

        oauthTextArea.setText(oauthText);
        oauthTextArea.setFont(mono.deriveFont(Font.ITALIC, 20));
        oauthTextArea.setEnabled(false);
        oauthTextArea.setDisabledTextColor(Color.BLACK);

        oauthButton.setFont(mono.deriveFont(20f));
        oauthButton.setEnabled(canOpenSystemBrowser);
        oauthButton.setVisible(canOpenSystemBrowser);

        oauthUrlArea.setEditable(false);
        oauthUrlArea.setLineWrap(true);
        oauthUrlArea.setOpaque(false);
        oauthUrlArea.setFont(mono.deriveFont(Font.ITALIC, 18));
        oauthUrlArea.setRows(1);

        layout.setHorizontalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*2.5))
                .addGroup(layout.createParallelGroup()
                    .addComponent(text, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(oauthUrlArea, GroupLayout.PREFERRED_SIZE, (int) (getPreferredSize().getWidth()*0.7) , GroupLayout.PREFERRED_SIZE)
                    .addComponent(oauthButton)
                    .addComponent(oauthTextArea)
                    )
                );

        layout.setVerticalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*1.5))
                .addComponent(text, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(50)
                .addGroup(layout.createParallelGroup()
                    .addComponent(oauthTextArea, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    )
                .addGroup(layout.createParallelGroup()
                    .addComponent(oauthButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    )
                .addGroup(layout.createParallelGroup()
                    .addComponent(oauthUrlArea, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    )
                );

        return panel;
    }

    private JPanel createAboutPanel(JPanel main) {
        var panel = new CheckeredPanel();

        var buttonPanel = new JPanel();
        initComponent(buttonPanel);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        var backButton = new JButton("Back");
        initComponent(backButton);
        backButton.setForeground(Color.BLACK);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        buttonPanel.add(backButton);

        backButton.addActionListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                getContentPane().remove(panel);
                getContentPane().add(main);
                pack();
                repaint();
            });
        });

        panel.setLayout(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        panel.add(center, BorderLayout.CENTER);

        var layout = new GroupLayout(center);
        center.setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        var version = new JLabel("Version:");
        initComponent(version);
        var source = new JLabel("Source:");
        initComponent(source);

        var teamCheck = new JLabel("Team Check");
        initComponent(teamCheck);
        teamCheck.setFont(Font.decode("PLAIN-30"));
        TeamChooser.class.getModule().getDescriptor().version().ifPresent(v -> teamCheck.setText(teamCheck.getText() + " " + v.toString()));
        var githubTeamcheck = new JTextField("https://github.com/tors42/teamcheck");
        initComponent(githubTeamcheck);
        githubTeamcheck.setEditable(false);
        githubTeamcheck.setBackground(null);
        githubTeamcheck.setFont(mono.deriveFont(Font.ITALIC));

        var about = new JTextArea();
        about.setOpaque(false);
        about.setText("""
                It can be a tedious task to go through all the members of one's
                teams, trying to keep the teams representable. This application
                can be used to easier visualize possible cases of dishonorable
                activities. In addition to detection, it is also possible to
                enable the possibility to take action, by visiting lichess.org
                and granting the application the right to remove team members -
                with a steady hand it can be as easy as a single click with the
                mouse!
                One can hope any removed members will redeem themselves and
                come back with newfound wisdom!""");
        about.setFont(Font.decode("PLAIN-30"));
        about.setEnabled(false);
        about.setDisabledTextColor(Color.BLACK);


        layout.setHorizontalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*2.5))
                .addGroup(layout.createParallelGroup()
                    .addComponent(version)
                    .addComponent(source)
                    )
                .addGroup(layout.createParallelGroup()
                    .addComponent(teamCheck)
                    .addComponent(githubTeamcheck)
                    .addComponent(about)
                    )
                );

        layout.setVerticalGroup(
                layout.createSequentialGroup().addGap((int) ((getPreferredSize().getWidth()/16)*2.5))
                .addGroup(layout.createParallelGroup()
                    .addComponent(version)
                    .addComponent(teamCheck))
                .addGroup(layout.createParallelGroup()
                    .addComponent(source)
                    .addComponent(githubTeamcheck, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addGap(40)
                    .addComponent(about)
                 );

        return panel;
    }

    private void initComponent(JComponent component) {
        component.setOpaque(false);
        component.setBackground(new Color(0,0,0, 0));
        component.setBorder(null);
        component.setForeground(Color.BLACK);
        if (component instanceof JLabel || component instanceof JButton) {
            component.setFont(mono);
        }
    }
}
