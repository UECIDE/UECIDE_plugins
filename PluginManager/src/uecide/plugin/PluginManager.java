package uecide.plugin;

import uecide.app.*;
import uecide.app.debug.*;
import uecide.app.editors.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import say.swing.*;
import org.json.simple.*;

public class PluginManager extends Plugin
{
    public static HashMap<String, String> pluginInfo = null;
    public static URLClassLoader loader = null;
    public static void setLoader(URLClassLoader l) { loader = l; }
    public static void setInfo(HashMap<String, String>info) { pluginInfo = info; }
    public static String getInfo(String item) { return pluginInfo.get(item); }


    JFrame win;
    JButton refreshButton;
    JScrollPane scroll;
    JSplitPane body;
    JButton upgradeAllButton;

    public static HashMap<String, JSONObject> availablePlugins = new HashMap<String, JSONObject>();
    public static HashMap<String, JSONObject> availableCores = new HashMap<String, JSONObject>();
    public static HashMap<String, JSONObject> availableBoards = new HashMap<String, JSONObject>();
    public static HashMap<String, JSONObject> availableCompilers = new HashMap<String, JSONObject>();

    public HashMap<String, PluginEntry> pluginObjects = new HashMap<String, PluginEntry>();
    public HashMap<String, PluginEntry> coreObjects = new HashMap<String, PluginEntry>();
    public HashMap<String, PluginEntry> boardObjects = new HashMap<String, PluginEntry>();
    public HashMap<String, PluginEntry> compilerObjects = new HashMap<String, PluginEntry>();

    public static HashMap<String, String> familyNames = new HashMap<String, String>();

    public static final int PLUGIN = 1;
    public static final int CORE = 2;
    public static final int BOARD = 3;
    public static final int COMPILER = 4;

    DefaultMutableTreeNode rootNode;
    DefaultTreeModel treeModel;
    JTree tree;
    JPanel infoPanel;
    JPanel downloadManager;
    Box dlmBox;
    

    public class PluginInfo {
        public String installed;
        public String available;
        public String url;
    }

    public void openMainWindow()
    {
        win = new JFrame(Translate.t("Plugin Manager"));
        win.getContentPane().setLayout(new BorderLayout());
        win.setResizable(true);

        downloadManager = new JPanel();
        downloadManager.setLayout(new BorderLayout());
        Border dlmSpace = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        dlmBox = Box.createVerticalBox();
        dlmBox.setBorder(dlmSpace);
        downloadManager.add(dlmBox);

        rootNode = new DefaultMutableTreeNode("UECIDE Plugins");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setPreferredSize(null);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);

        tree.expandRow(0);
        tree.setRootVisible(true);

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
                if (node == null) {
                    return;
                }
                Object uo = node.getUserObject();

                if (uo instanceof PluginEntry) {
                    PluginEntry pe = (PluginEntry)node.getUserObject();

                    if (pe != null) {
                        infoPanel.removeAll();
                        win.repaint();
                        win.pack();
                        JLabel l = new JLabel(pe.getDescription());
                        infoPanel.add(l, BorderLayout.NORTH);
                        if (pe.isOutdated() || pe.isNewer()) {
                            l = new JLabel("Installed: " + pe.getInstalledVersion() + " Available: " + pe.getAvailableVersion());
                        } else if (pe.isInstalled()) {
                            l = new JLabel("Installed: " + pe.getInstalledVersion());
                        } else {
                            l = new JLabel("Available: " + pe.getAvailableVersion());
                        }
                        infoPanel.add(l, BorderLayout.SOUTH);
                        infoPanel.add(pe, BorderLayout.CENTER);
                        win.repaint();
                        win.pack();
                    }
                }
            }
        });


        PluginNodeRenderer renderer = new PluginNodeRenderer();
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(renderer);

        scroll = new JScrollPane(tree);
        infoPanel = new JPanel(new BorderLayout());

        body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, infoPanel);
        body.setAlignmentX(Component.CENTER_ALIGNMENT);

        body.setResizeWeight(0.7);

        Box box = Box.createVerticalBox();
        box.add(body);

        Box line = Box.createHorizontalBox();
        upgradeAllButton = new JButton(Translate.t("Upgrade All"));
        upgradeAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                upgradeAll();
            }
        });
        line.add(upgradeAllButton);

        refreshButton = new JButton(Translate.t("Refresh"));
        refreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updatePlugins();
            }
        });
        line.add(refreshButton);


        box.add(line);


        Border dlBorder = BorderFactory.createLoweredBevelBorder();
        downloadManager.setBorder(dlBorder);

        box.add(downloadManager);
        

        win.getContentPane().add(box);
        win.pack();

        Dimension size = new Dimension(500, 500); //win.getSize();
//        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
//        win.setSize(size);
        win.setMinimumSize(size);
//        win.setMaximumSize(size);
//        win.setPreferredSize(size);
//        win.setLocation((screen.width - size.width) / 2,
//                          (screen.height - size.height) / 2);

        win.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        win.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        Base.setIcon(win);

        win.setVisible(true);

        if (PluginManager.availablePlugins.size() == 0) {
            updatePlugins();
        } else {
            populate();
        }
    }

    public TreePath findTreeNode(TreePath parent, Object[] nodes, int depth, boolean byName) {
        TreeNode node = (TreeNode)parent.getLastPathComponent();
        Object o = node;

        // If by name, convert node to a string
        if (byName) {
            o = o.toString();
        }

        // If equal, go down the branch
        if (o.equals(nodes[depth])) {
            // If at end, return match
            if (depth == nodes.length-1) {
                return parent;
            }

            // Traverse children
            if (node.getChildCount() >= 0) {
                for (Enumeration e=node.children(); e.hasMoreElements(); ) {
                    TreeNode n = (TreeNode)e.nextElement();
                    TreePath path = parent.pathByAddingChild(n);
                    TreePath result = findTreeNode(path, nodes, depth+1, byName);
                    // Found a match
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        // No match at this branch
        return null;
    }


    public void populate() {

        // ---- Plugins ---- //

        tree.setEnabled(false);

        DefaultMutableTreeNode pluginsNode;

        rootNode.removeAllChildren();

        TreePath pluginsPath = findTreeNode(new TreePath(treeModel.getRoot()), new String[] {"root", Translate.t("Plugins")}, 0, true);
        if (pluginsPath == null) {
            pluginsNode = new DefaultMutableTreeNode(Translate.t("Plugins"));
            rootNode.add(pluginsNode);
        } else {
            pluginsNode = (DefaultMutableTreeNode)pluginsPath.getLastPathComponent();
        }

        for (String entry : PluginManager.availablePlugins.keySet().toArray(new String[0])) {
            JSONObject plugin = PluginManager.availablePlugins.get(entry);
            PluginEntry pe = pluginObjects.get(entry);
            if (pe == null) {
                pe = new PluginEntry(plugin, 1);
            }

            TreePath searchPath = findTreeNode(new TreePath(treeModel.getRoot()), new Object[] {"root", Translate.t("Plugins"), pe}, 0, true);
            DefaultMutableTreeNode thisNode;
            if (searchPath == null) {
                thisNode = new DefaultMutableTreeNode(pe);
                thisNode.setUserObject(pe);
                pluginsNode.add(thisNode);
            } else {
                thisNode = (DefaultMutableTreeNode)searchPath.getLastPathComponent();
                thisNode.setUserObject(pe);
            }
            pluginObjects.put(entry, pe);
        }

        // ---- Gather families ---- //

        HashMap<String, DefaultMutableTreeNode> families = new HashMap<String, DefaultMutableTreeNode>();

        String[] entries = PluginManager.availableBoards.keySet().toArray(new String[0]);
        for (String entry : entries) {
            JSONObject plugin = PluginManager.availableBoards.get(entry);
            String family = (String)plugin.get("Family");
            String famarr[] = family.split(",");
            for (String fam : famarr) {
                if (families.get(fam) == null) {
                    String fname = PluginManager.familyNames.get(fam);
                    if (fname == null) {
                        fname = fam;
                    }
                    families.put(fam, new DefaultMutableTreeNode(fname));
                }
            }
        }

        entries = PluginManager.availableCores.keySet().toArray(new String[0]);
        for (String entry : entries) {
            JSONObject plugin = PluginManager.availableCores.get(entry);
            String family = (String)plugin.get("Family");
            String famarr[] = family.split(",");
            for (String fam : famarr) {
                if (families.get(fam) == null) {
                    String fname = PluginManager.familyNames.get(fam);
                    if (fname == null) {
                        fname = fam;
                    }
                    families.put(fam, new DefaultMutableTreeNode(fname));
                }
            }
        }

        entries = PluginManager.availableCompilers.keySet().toArray(new String[0]);
        for (String entry : entries) {
            JSONObject plugin = PluginManager.availableCompilers.get(entry);
            String family = (String)plugin.get("Family");
            String famarr[] = family.split(",");
            for (String fam : famarr) {
                if (families.get(fam) == null) {
                    String fname = PluginManager.familyNames.get(fam);
                    if (fname == null) {
                        fname = fam;
                    }
                    families.put(fam, new DefaultMutableTreeNode(fname));
                }
            }
        }

        String[] fent = families.keySet().toArray(new String[0]);
        Arrays.sort(fent);

        for (String f : fent) {
            DefaultMutableTreeNode froot = families.get(f);
            rootNode.add(froot);
            DefaultMutableTreeNode boardRoot = new DefaultMutableTreeNode(Translate.t("Boards"));
            froot.add(boardRoot);

            ArrayList<String> groups = new ArrayList<String>();
            entries = PluginManager.availableBoards.keySet().toArray(new String[0]);
            for (String entry : entries) {
                JSONObject plugin = PluginManager.availableBoards.get(entry);
                String eFam = (String)plugin.get("Family");
                String eGrp = (String)plugin.get("Group");
                String[] efs = eFam.split(",");
                for (String ef : efs) {
                    if (f.equals(ef)) {
                        if (groups.indexOf(eGrp) == -1) {
                            groups.add(eGrp);
                        }
                    }
                }
            }
            Collections.sort(groups);     

            for (String group : groups) {
                DefaultMutableTreeNode grpNode = new DefaultMutableTreeNode(group);
                boardRoot.add(grpNode);

                ArrayList<String> validBoards = new ArrayList<String>();
                for (String entry : entries) {
                    JSONObject plugin = PluginManager.availableBoards.get(entry);
                    String eFam = (String)plugin.get("Family");
                    String eGrp = (String)plugin.get("Group");
                    String efs[] = eFam.split(",");
                    for (String ef : efs) {
                        if (ef.equals(f) && eGrp.equals(group)) {
                            if (validBoards.indexOf(entry) == -1) {
                                validBoards.add(entry);
                            }
                        }
                    }
                }

                Collections.sort(validBoards, new Comparator() {
                    public int compare(Object a, Object b) {
                        String s1 = (String)a;
                        String s2 = (String)b;
                        JSONObject p1 = PluginManager.availableBoards.get(s1);
                        JSONObject p2 = PluginManager.availableBoards.get(s2);
                        String t1 = ((String)p1.get("Description")).toLowerCase();
                        String t2 = ((String)p2.get("Description")).toLowerCase();
                        return t1.compareTo(t2);
                    }
                });
                for (String board : validBoards) {
                    JSONObject plugin = PluginManager.availableBoards.get(board);
                    PluginEntry pe = boardObjects.get(board);
                    if (pe == null) {
                        pe = new PluginEntry(plugin, 3);
                    }
                    DefaultMutableTreeNode brdNode = new DefaultMutableTreeNode(pe);
                    brdNode.setUserObject(pe);
                    grpNode.add(brdNode);
                    boardObjects.put(board, pe);
                }
            }

            DefaultMutableTreeNode coreRoot = new DefaultMutableTreeNode(Translate.t("Cores"));
            froot.add(coreRoot);
            entries = PluginManager.availableCores.keySet().toArray(new String[0]);
            for (String entry : entries) {
                JSONObject plugin = PluginManager.availableCores.get(entry);
                String eFam = (String)plugin.get("Family");
                String efs[] = eFam.split(",");
                for (String ef : efs) {
                    if (f.equals(ef)) {
                        JSONObject plugin1 = PluginManager.availableCores.get(entry);
                        PluginEntry pe = coreObjects.get(entry);
                        if (pe == null) {
                            pe = new PluginEntry(plugin1, 2);
                        }
                        DefaultMutableTreeNode brdNode = new DefaultMutableTreeNode(pe);
                        brdNode.setUserObject(pe);
                        coreRoot.add(brdNode);
                        coreObjects.put(entry, pe);
                    }
                }
            }

            // ---- Compilers ---- //

            DefaultMutableTreeNode compilerRoot = new DefaultMutableTreeNode(Translate.t("Compilers"));
            froot.add(compilerRoot);

            for (String entry : PluginManager.availableCompilers.keySet().toArray(new String[0])) {
                JSONObject plugin = PluginManager.availableCompilers.get(entry);
                String eFam = (String)plugin.get("Family");
                String efs[] = eFam.split(",");
                for (String ef : efs) {
                    if (f.equals(ef)) {
                        PluginEntry pe = compilerObjects.get(entry);
                        if (pe == null) {
                            pe = new PluginEntry(plugin, 4);
                        }
                        DefaultMutableTreeNode brdNode = new DefaultMutableTreeNode(pe);
                        brdNode.setUserObject(pe);
                        compilerRoot.add(brdNode);
                        compilerObjects.put(entry, pe);
                    }
                }
            }
        }

        tree.expandRow(0);
        tree.setRootVisible(true);
        treeModel.reload();
        tree.setEnabled(true);
    }

    public class PluginNodeRenderer extends DefaultTreeCellRenderer {
        DefaultTreeCellRenderer nonLeafRenderer = new DefaultTreeCellRenderer();
        JLabel name = new JLabel("");
        Icon installed;
        Icon available;
        Icon downloading;
        Icon queued;
        Icon upgrade;
        Icon newer;

        public PluginNodeRenderer() {
            installed = Base.loadIconFromResource("uecide/plugin/PluginManager/installed.png", loader);
            available = Base.loadIconFromResource("uecide/plugin/PluginManager/available.png", loader);
            downloading = Base.loadIconFromResource("uecide/plugin/PluginManager/downloading.png", loader);
            queued = Base.loadIconFromResource("uecide/plugin/PluginManager/queued.png", loader);
            upgrade = Base.loadIconFromResource("uecide/plugin/PluginManager/upgrade.png", loader);
            newer = Base.loadIconFromResource("uecide/plugin/PluginManager/newer.png", loader);
        }
        
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object o = null;
            if (value instanceof DefaultMutableTreeNode) {
                o = ((DefaultMutableTreeNode)value).getUserObject();
            }

            if (o == null) {
                return nonLeafRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            }

            if (o instanceof PluginEntry) {
                PluginEntry pe = (PluginEntry)o;
                String text = pe.getDisplayName();
                name.setText(text);
                if (pe.isDownloading()) {
                    name.setIcon(downloading);
//                } else if (pe.isQueued()) {
//                    name.setIcon(queued);
                } else if (pe.isNewer()) {
                    name.setIcon(newer);
                } else if (pe.isOutdated()) {
                    name.setIcon(upgrade);
                } else if (pe.isInstalled()) {
                    name.setIcon(installed);
                } else {
                    name.setIcon(available);
                }

                if (Base.isWindows()) {
                    if (selected) {
                        name.setOpaque(true);
                        name.setBackground(new Color(100,100,200));
                        name.setForeground(new Color(255,255,255));
                    } else {
                        name.setOpaque(true);
                        name.setBackground(new Color(255,255,255));
                        name.setForeground(new Color(0,0,0)); 
                    }
                } else {
                    name.setOpaque(false);
                }
                
                return name;
            } else if (o instanceof String) {
                name.setText((String)o);
                name.setOpaque(false);
                name.setForeground(new Color(0,0,0)); 
                name.setIcon(null);
                return name;
            }
            return nonLeafRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
    }

    public boolean isDownloading() {
        boolean isDownloading = false;

        for (String e : pluginObjects.keySet()) {
            if (pluginObjects.get(e).isDownloading()) {
                isDownloading = true;
            }
        }

        for (String e : boardObjects.keySet()) {
            if (boardObjects.get(e).isDownloading()) {
                isDownloading = true;
            }
        }

        for (String e : coreObjects.keySet()) {
            if (coreObjects.get(e).isDownloading()) {
                isDownloading = true;
            }
        }

        for (String e : compilerObjects.keySet()) {
            if (compilerObjects.get(e).isDownloading()) {
                isDownloading = true;
            }
        }

        return isDownloading;

    }

    public void cancelDownloads() {

        for (String e : pluginObjects.keySet().toArray(new String[0])) {
            pluginObjects.get(e).cancelAll();
        }

        for (String e : boardObjects.keySet().toArray(new String[0])) {
            boardObjects.get(e).cancelAll();
        }

        for (String e : coreObjects.keySet().toArray(new String[0])) {
            coreObjects.get(e).cancelAll();
        }

        for (String e : compilerObjects.keySet().toArray(new String[0])) {
            compilerObjects.get(e).cancelAll();
        }
    }

    public void close()
    {
//        cancelDownloads();
        if (isDownloading()) {
            String downloads = "";
            for (String e : pluginObjects.keySet()) {
                if (pluginObjects.get(e).isDownloading()) {
                    if (!downloads.equals("")) downloads += ", ";
                    downloads += pluginObjects.get(e).getDisplayName();
                }
            }

            for (String e : boardObjects.keySet()) {
                if (boardObjects.get(e).isDownloading()) {
                    if (!downloads.equals("")) downloads += ", ";
                    downloads += boardObjects.get(e).getDisplayName();
                }
            }

            for (String e : coreObjects.keySet()) {
                if (coreObjects.get(e).isDownloading()) {
                    if (!downloads.equals("")) downloads += ", ";
                    downloads += coreObjects.get(e).getDisplayName();
                }
            }

            for (String e : compilerObjects.keySet()) {
                if (compilerObjects.get(e).isDownloading()) {
                    if (!downloads.equals("")) downloads += ", ";
                    downloads += compilerObjects.get(e).getDisplayName();
                }
            }


            Base.showWarning(Translate.t("Downloading in progress"), Translate.w("You have active downloads at the moment.  You must wait for them to finish before you close this window.\nDownloads: %1", 40, "\n", downloads), null);
            return;
        }
        win.dispose();
        
        int p = Base.pluginInstances.indexOf(this);
        if (p>=0) {
            Base.pluginInstances.remove(p);
        }

    }

    public void message(String m) {
    }
    
    public void message(String m, int c) {
        message(m);
    }

    public void updatePlugins() {
        SwingWorker sw = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() {
                tree.setEnabled(false);
                refreshButton.setEnabled(false);
                String data = null;
                try {
                    URL page = new URL(Base.theme.get("plugins.url") + "?platform=" + Base.getOSName() + "&arch=" + Base.getOSArch());
                    BufferedReader in = new BufferedReader(new InputStreamReader(page.openStream()));
                    data = in.readLine();
                    in.close();
                } catch (UnknownHostException e) {
                    Base.showWarning(Translate.t("Update Failed"), Translate.w("The update failed because I could not find the host %1", 40, "\n", e.getMessage()), e);
                    return null;
                } catch (Exception e) {
                    Base.showWarning(Translate.t("Update Failed"), Translate.w("An unknown error occurred: %1", 40, "\n", e.toString()), e);
                    return null;
                }

                availablePlugins = new HashMap<String, JSONObject>();
                availableCores = new HashMap<String, JSONObject>();
                availableBoards = new HashMap<String, JSONObject>();
                availableCompilers = new HashMap<String, JSONObject>();

                pluginObjects = new HashMap<String, PluginEntry>();
                coreObjects = new HashMap<String, PluginEntry>();
                boardObjects = new HashMap<String, PluginEntry>();
                compilerObjects = new HashMap<String, PluginEntry>();

                JSONObject ob = (JSONObject)JSONValue.parse(data);
                try {
                    JSONObject plugins = (JSONObject)ob.get("plugins");
                    PluginManager.availablePlugins.putAll(plugins);
                } catch (Exception ignored) {}
                try {
                    JSONObject cores = (JSONObject)ob.get("cores");
                    PluginManager.availableCores.putAll(cores);
                } catch (Exception ignored) {}
                try {
                    JSONObject boards = (JSONObject)ob.get("boards");
                    PluginManager.availableBoards.putAll(boards);
                } catch (Exception ignored) {}
                try {
                    JSONObject compilers = (JSONObject)ob.get("compilers");
                    PluginManager.availableCompilers.putAll(compilers);
                } catch (Exception ignored) {}

                try {
                    JSONObject fams = (JSONObject)ob.get("families");
                    PluginManager.familyNames.putAll(fams);
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
                 
                populate();
                refreshButton.setEnabled(true);
                return null;
            }
        };
        sw.execute();
    }

    public File getJarFileToTmp(String name, String url) {
        try {
            File dest = new File(Base.getTmpDir(), name + ".jar");
            URL page = new URL(url);
            InputStream in = page.openStream();
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            out.close();
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    public void populateMenu(JMenu menu, int flags) {
        if (flags == (Plugin.MENU_TOOLS | Plugin.MENU_TOP)) {
            JMenuItem item = new JMenuItem("Plugin Manager");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    openMainWindow();
                }
            });
            menu.add(item);
        }
    }

    public void upgradeAll() {
        for (PluginEntry pe : pluginObjects.values()) {
            if (pe.isOutdated()) {
                pe.startDownload();
            }
        }

        for (PluginEntry pe : boardObjects.values()) {
            if (pe.isOutdated()) {
                pe.startDownload();
            }
        }

        for (PluginEntry pe : coreObjects.values()) {
            if (pe.isOutdated()) {
                pe.startDownload();
            }
        }

        for (PluginEntry pe : compilerObjects.values()) {
            if (pe.isOutdated()) {
                pe.startDownload();
            }
        }

    }

    public class PluginEntry extends JPanel implements ActionListener {
        public String name;
        public Version installedVersion;
        public Version availableVersion;
        public String url;
        JButton button;
        JLabel label;
        JProgressBar bar;
        JProgressBar dlmBar;
        Box dlmEntry;
        public int type;
        File dest;
        public String mainClass;
        public JSONObject data;
        public boolean isDownloading = false;

        SwingWorker<Void, Long> downloader = null;
        ZipExtractor installer = null;


        PluginEntry installNext = null;

        public PluginEntry(JSONObject o, int type) {
            try {
                data = o;
                this.type = type;
                url = (String)o.get("url");
                availableVersion = new Version((String)o.get("Version"));
                if (availableVersion == null) {
                    availableVersion = new Version(null);
                }

                installedVersion = null;

                if (type == PluginManager.PLUGIN) {
                    mainClass = (String)o.get("Main-Class");
                    name = mainClass.substring(mainClass.lastIndexOf(".")+1);
                    installedVersion = Base.getPluginVersion(mainClass);
                } 

                if (type == PluginManager.CORE) {
                    name = (String)o.get("Core");
                    Core c = Base.cores.get(name);
                    if (c != null) {    
                        installedVersion = new Version(c.getFullVersion());
                    }
                }

                if (type == PluginManager.BOARD) {
                    name = (String)o.get("Board");
                    Board c = Base.boards.get(name);
                    if (c != null) {    
                        installedVersion = new Version(c.getFullVersion());
                    }
                }

                if (type == PluginManager.COMPILER) {
                    name = (String)o.get("Compiler");
                    uecide.app.debug.Compiler c = Base.compilers.get(name);
                    if (c != null) {    
                        installedVersion = new Version(c.getFullVersion());
                    }
                }

                updateDisplay();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    
        public void updateDisplay() {

            this.removeAll();
            repaint();
            win.repaint();
            win.pack();

            if (!isOutdated() && !isInstalled() && !isNewer()) {
                button = new JButton("Install");
                button.addActionListener(this);
                button.setActionCommand("install");
                this.add(button);
            } else if (isInstalled()) {
                label = new JLabel("Installed");
                this.add(label);
            } else if (isNewer()) {
                label = new JLabel("Test Version");
                this.add(label);
            } else if (isOutdated()) {
                button = new JButton("Upgrade");
                button.addActionListener(this);
                button.setActionCommand("upgrade");
                this.add(button);
            }

            if (isOutdated() || isInstalled() || isNewer()) {
                button = new JButton("Uninstall");
                button.addActionListener(this);
                button.setActionCommand("uninstall");
                this.add(button);
            }
            repaint();
            win.repaint();
            win.pack();
        }
        
        public boolean isNewer() {
            if (installedVersion == null) {
                return false;
            }
            if (installedVersion.compareTo(availableVersion) > 0) {
                return true;
            }
            return false;
        }

        public boolean isOutdated() {
            if (installedVersion == null) {
                return false;
            }
            if (installedVersion.compareTo(availableVersion) < 0) {
                return true;
            }
            return false;
        }

        public boolean isInstalled() {
            if (installedVersion == null) {
                return false;
            }
            if (installedVersion.compareTo(availableVersion) == 0) {
                return true;
            }
            return false;
        }

        public String get(String k) {
            return (String)data.get(k);
        }

        public String getAvailableVersion() {
            return availableVersion.toString();
        }

        public String getInstalledVersion() {
            if (installedVersion == null) {
                return "";
            }
            return installedVersion.toString();
        }

        public String toString() {
            return getDisplayName();
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            switch (type) {
                case 1:
                    return name;
                case 2:
                    return name;
                case 3:
                    return get("Description");
                case 4:
                    return name;
            }
            return "---";
        }
 
        public String getDescription() {
            String d = get("Description");
            if (d == null) {
                return getDisplayName();
            }
            return d;
        }

        public void cancelAll() {   
            if (downloader != null) {
                downloader.cancel(true);
            }
            if (installer != null) {
                installer.cancel(true);
            }
            isDownloading = false;
        }

        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();
            button.setEnabled(false);
            if (command.equals("install") || command.equals("upgrade")) {

                if (type == PluginManager.BOARD) {
                    String recommendedCore = (String)data.get("Core");
                    if (recommendedCore != null) {
                        PluginEntry recommendedCoreObject = null;

                        for (String ent : coreObjects.keySet()) {
                            PluginEntry pent = coreObjects.get(ent);
                            if (pent.getName().equals(recommendedCore)) {
                                recommendedCoreObject = pent;
                            }
                        }
                        if (recommendedCoreObject != null) {
                            if (!(recommendedCoreObject.isInstalled() || recommendedCoreObject.isDownloading())) {
                                int n = JOptionPane.showConfirmDialog(
                                    win,
                                    Translate.w("%1 recommends you use the %2 core, which you do not have installed.  Do you want to install %2?", 40, "\n", this.getDisplayName(), recommendedCoreObject.toString()),
                                    Translate.t("Recommended core not installed"),
                                    JOptionPane.YES_NO_OPTION
                                );
                                if (n == JOptionPane.YES_OPTION) {
                                    recommendedCoreObject.actionPerformed(e);
                                }
                            }
                        }
                    }
                } else if (type == PluginManager.CORE) {
                    String recommendedCompiler = (String)data.get("Compiler");
                    if (recommendedCompiler != null) {
                        PluginEntry recommendedCompilerObject = null;

                        for (String ent : compilerObjects.keySet()) {
                            PluginEntry pent = compilerObjects.get(ent);
                            if (pent.getName().equals(recommendedCompiler)) {
                                recommendedCompilerObject = pent;
                            }
                        }
                        if (recommendedCompilerObject != null) {
                            if (!(recommendedCompilerObject.isInstalled() || recommendedCompilerObject.isDownloading())) {
                                int n = JOptionPane.showConfirmDialog(
                                    win,
                                    Translate.w("%1 recommends you use the %2 compiler, which you do not have installed.  Do you want to install %2?", 40, "\n", this.getDisplayName(), recommendedCompilerObject.toString()),
                                    Translate.t("Recommended compiler not installed"),
                                    JOptionPane.YES_NO_OPTION
                                );
                                if (n == JOptionPane.YES_OPTION) {
                                    recommendedCompilerObject.actionPerformed(e);
                                }
                            }
                        }
                    }
                }

                startDownload();
            }
            if (command.equals("uninstall")) {
                uninstall();
            }
        }

        public void uninstall() {
            if (type == PluginManager.PLUGIN) {
                if (mainClass.equals(PluginManager.this.getClass().getName())) {
                    Base.showWarning(Translate.t("Unable To Uninstall"), Translate.w("If you uninstall the Plugin Manager you won't be able to install any new plugins. That would be a bit silly, don't you think? I'm not going to let you do it.", 40, "\n"), null);
                    return;
                }
                File jf = new File(Base.getPluginInfo(mainClass, "jarfile"));
                if (jf.exists()) {
                    jf.delete();
                    installedVersion = null;
                }
            }

            if (type == PluginManager.BOARD) {
                Board b = Base.boards.get(name);
                if (b != null) {
                    File bf = b.getFolder();
                    if (bf.exists() && bf.isDirectory()) {
                        Base.removeDir(bf);
                        installedVersion = null;
                    }
                }
            }

            if (type == PluginManager.CORE) {
                Core c = Base.cores.get(name);
                if (c != null) {
                    File cf = c.getFolder();
                    if (cf.exists() && cf.isDirectory()) {
                        Base.removeDir(cf);
                        installedVersion = null;
                    }
                }
            }

            if (type == PluginManager.COMPILER) {
                uecide.app.debug.Compiler c = Base.compilers.get(name);
                if (c != null) {
                    File cf = c.getFolder();
                    if (cf.exists() && cf.isDirectory()) {
                        Base.removeDir(cf);
                        installedVersion = null;
                    }
                }
            }

            Base.loadCompilers();
            Base.loadCores();
            Base.loadBoards();
            Base.gatherLibraries();
            for (Editor e : Base.editors) {
                e.updateAll();
            }

            updateDisplay();

        }

        public void startDownload(PluginEntry pe) {
            installNext = pe;
            startDownload();
        }

        public void startDownload() {
            this.removeAll();
            bar = new JProgressBar(0, 100);
            dlmBar = new JProgressBar(0, 100);
            dlmBar.setSize(new Dimension(100, 20));
            dlmBar.setPreferredSize(new Dimension(100, 20));
            dlmBar.setMinimumSize(new Dimension(100, 20));
            dlmBar.setMaximumSize(new Dimension(100, 20));
            dlmEntry = Box.createHorizontalBox();
            JLabel lab = new JLabel(getDisplayName());
            dlmEntry.add(lab);
            dlmEntry.add(Box.createHorizontalGlue());
            dlmEntry.add(dlmBar);
            dlmBox.add(dlmEntry);
            repaint();
            win.repaint();
            win.pack();
            

//            if (type == PluginManager.CORE) {
//                if (Base.compilers.get((String)data.get("Compiler")) == null) {
//                    PluginEntry pe = compilerObjects.get((String)data.get("Compiler"));
//                    if (pe != null) {
//                        pe.startDownload(this);
//                        bar.setIndeterminate(false);
//                        bar.setString("Installing Compiler...");
//                        bar.setStringPainted(true);
//                        this.add(bar);
//                        repaint();
//                        win.repaint();
//                        win.pack();
//                        return;
//                    } else {
//                        Base.showWarning(Translate.t("Unable to install"), Translate.w("That core cannot be installed right now. You do not have the compiler installed, and I cannot find the compiler in my list of packages. Try refreshing the list and trying again.", 40, "\n"), null);
//                        return;
//                    }
//                }
//            }
            bar.setString("Downloading");
            dlmBar.setString("Downloading");
            bar.setStringPainted(true);
            dlmBar.setStringPainted(true);
            bar.setIndeterminate(false);
            dlmBar.setIndeterminate(false);
            this.add(bar);
            repaint();
            win.repaint();
            win.pack();

            download();
        }

        public void setProgress(long p) {
            if (dlmBar != null) {
                if (p == -1) {
                    dlmBar.setIndeterminate(true);
                } else {
                    dlmBar.setIndeterminate(false);
                    dlmBar.setValue((int)p);
                }
            }
            if (bar != null) {
                if (p == -1) {
                    bar.setIndeterminate(true);
                } else {
                    bar.setIndeterminate(false);
                    bar.setValue((int)p);
                }
            }
            if (installNext != null) {
                installNext.setProgress(p);
            }
        }

        public void setMax(long m) {
            if (dlmBar != null) {
                dlmBar.setMaximum((int)m);
            }
            if (bar != null) {
                bar.setMaximum((int)m);
            }
            if (installNext != null) {
                installNext.setMax(m);
            }
        }

        public void download() {

            isDownloading = true;

            try {
                
                dest = new File(Base.getTmpDir(), name + ".jar");
                URL page = new URL(url);
                HttpURLConnection httpConn = (HttpURLConnection) page.openConnection();
                final long contentLength = httpConn.getContentLength();
                final InputStream in = httpConn.getInputStream();
                final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest));

                setMax(contentLength);

                downloader = new SwingWorker<Void, Long>(){
                    @Override
                    public Void doInBackground() {
                        try {
                            byte[] buffer = new byte[1024];
                            int n;
                            long tot = 0;
                            while ((n = in.read(buffer)) > 0) {
                                tot += n;
                                publish(tot);
                                out.write(buffer, 0, n);
                            }
                        } catch (Exception ex) {
                            Base.showWarning(Translate.t("Download Failed"), Translate.w("The download failed at point 1 because %1", 40, "\n", ex.toString()), ex);
                            isDownloading = false;
                        }
                        return null;
                    }

                    @Override
                    public void done() {
                        try {
                            in.close();
                            out.close();
                        } catch (Exception ex) {
                            Base.showWarning(Translate.t("Download Failed"), Translate.w("The download failed at point 2 because %1", 40, "\n", ex.toString()), ex);
                            isDownloading = false;
                            return;
                        }
                        PluginEntry.this.install();
                    }

                    @Override
                    protected void process(java.util.List<Long> chunk) {
                        for (long num : chunk) {
                            PluginEntry.this.setProgress(num);
                        }
                    }
                
                };
                downloader.execute();
            } catch (Exception e) {
                Base.error(e);
                Base.showWarning(Translate.t("Download Failed"), Translate.w("The download failed at point 3 because %1", 40, "\n", e.toString()), e);
                isDownloading = false;
            }
        }

        public void install() {
            bar.setString("Installing");
            dlmBar.setString("Installing");
            setProgress(0);

            if (type == PluginManager.PLUGIN) {
                try {
                    Base.copyFile(dest, new File(Base.getUserPluginsFolder(), dest.getName()));
                    setProgress(100);
                    Base.reloadPlugins();
                    setInstalled();
                } catch (Exception e) {
                    Base.error(e);
                    Base.showWarning(Translate.t("Install Failed"), Translate.w("The install failed because %1", 40, "\n", e.toString()), e);
                }
            }

            if (type == PluginManager.CORE) {
                if (isOutdated() || isInstalled()) {
                    uninstall();
                }
                installer = new ZipExtractor(dest, Base.getUserCoresFolder(), this);
                installer.execute();
            }

            if (type == PluginManager.BOARD) {
                if (isOutdated() || isInstalled()) {
                    uninstall();
                }
                installer = new ZipExtractor(dest, Base.getUserBoardsFolder(), this);
                installer.execute();
            }

            if (type == PluginManager.COMPILER) {
                if (isOutdated() || isInstalled()) {
                    uninstall();
                }
                installer = new ZipExtractor(dest, Base.getUserCompilersFolder(), this);
                installer.execute();
            }
            
        }

        public void setDownloading() {
            isDownloading = true;

            updateDisplay();
            repaint();
            win.repaint();
            win.pack();
        }

        public void setInstalled() {
            isDownloading = false;
            installedVersion = availableVersion;
            updateDisplay();

            dlmBox.remove(dlmEntry);

            repaint();
            win.repaint();
            win.pack();
        }

        public boolean isDownloading() {
            return isDownloading;
        }
        
    }

    public static class ZipExtractor extends SwingWorker<Void, Integer>
    {
        File inputFile;
        File destination;
        PluginEntry pi;

        public ZipExtractor(File in, File out, PluginEntry p) {
            this.inputFile = in;
            this.destination = out;
            this.pi = p;
        }

        public ZipExtractor(String in, String out) {
            this.inputFile = new File(in);
            this.destination = new File(out);
        }

        @Override
        protected Void doInBackground() {
            byte[] buffer = new byte[1024];
            ArrayList<String> fileList = new ArrayList<String>();
            publish(-1);
            int files = Base.countZipEntries(inputFile);
            pi.setMax((long)files);
            if (files == -1) {
                Base.showWarning(Translate.t("Install Failed"), Translate.w("The install failed: The jar file has no entries.", 40, "\n"), null);
                return null;
            }
            int done = 0;
            try {
                ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
                ZipEntry ze = zis.getNextEntry();
                while (ze != null) {
                    String fileName = ze.getName();
                    File newFile = new File(destination, fileName);

                    new File(newFile.getParent()).mkdirs();

                    if (ze.isDirectory()) {
                        newFile.mkdirs();
                    } else {

                        FileOutputStream fos = new FileOutputStream(newFile);
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        newFile.setExecutable(true, false);
                    }
                    done++;
                    publish(done);
                    ze = zis.getNextEntry();
                    Thread.yield();
                }
                zis.closeEntry();
                zis.close();
            } catch (Exception e) {
                Base.error(e);
                Base.showWarning(Translate.t("Install Failed"), Translate.w("The install failed because %1", 40, "\n", e.toString()), e);
                return null;
            }
            return null;
        }

        @Override
        protected void done() {
            Base.loadCompilers();
            Base.loadCores();
            Base.loadBoards();
            Base.gatherLibraries();
            for (Editor e : Base.editors) {
                e.updateAll();
            }
            pi.setInstalled();
        }

        @Override
        protected void process(java.util.List<Integer> pct) {
            int p = pct.get(pct.size() - 1);
            pi.setProgress(p);
        }
    };

    public JButton getToolbarButton(int flags, String x) {
        return null;
    }

    public void addToolbarButtons(JToolBar tb, int flags) {
        if (flags == Plugin.TOOLBAR_EDITOR) {
            JButton b = new JButton(Base.loadIconFromResource("uecide/plugin/PluginManager/newer.png", loader));
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    openMainWindow();
                }
            });
            tb.add(b);
        }
    }


    public PluginManager(Editor e) { editor = e; }
    public PluginManager(EditorBase e) { editorTab = e; }

    public void launch() {
        openMainWindow();
    }
}

