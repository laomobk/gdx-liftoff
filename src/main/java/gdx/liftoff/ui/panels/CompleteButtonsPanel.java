package gdx.liftoff.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Align;
import com.ray3k.stripe.PopTable;
import gdx.liftoff.ui.UserData;
import gdx.liftoff.ui.dialogs.FullscreenDialog;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

import static gdx.liftoff.Main.*;

/**
 * The table to display the buttons to create a new project, open the project in an IDE, or exit the application after
 * project generation is complete.
 */
public class CompleteButtonsPanel extends Table implements Panel {
    /**
     * The PopTable to hide after the user clicks a button
     */
    PopTable popTable;

    String intellijPath = null;
    boolean intellijIsFlatpak = false;
    String androidStudioPath = null;
    boolean androidStudioIsFlatpak = false;

    private static final List<String> manuallyInstalledLinuxIdeaNames = Arrays.asList(
        "idea",
        "intellij-idea-ultimate-edition",
        "intellij-idea-community-edition"
    );

    public CompleteButtonsPanel(boolean fullscreen) {
        this(null, fullscreen);
    }

    public CompleteButtonsPanel(PopTable popTable, boolean fullscreen) {
        this.popTable = popTable;
        populate(fullscreen);
    }

    @Override
    public void populate(boolean fullscreen) {
        defaults().space(SPACE_MEDIUM);

        //new project button
        TextButton textButton = new TextButton(prop.getProperty("newProject"), skin, "big");
        add(textButton);
        addTooltip(textButton, Align.top, TOOLTIP_WIDTH, prop.getProperty("newProjectTip"));
        addHandListener(textButton);
        if (!fullscreen) onChange(textButton, () -> root.transitionTable(root.landingTable, true));
        else {
            onChange(textButton, () -> {
                popTable.hide();
                FullscreenDialog.show();
                root.showTableInstantly(root.landingTable);
            });
        }

        row();
        Table table = new Table();
        add(table);

        //idea button
        table.defaults().fillX().space(SPACE_MEDIUM);
        final TextButton ideaButton = new TextButton(prop.getProperty("openIdea"), skin);
        table.add(ideaButton);
        addHandListener(ideaButton);
        try {
            if (UIUtils.isWindows) {
                List<String> findIntellijExecutable = Arrays.asList("where.exe", "idea.cmd");

                Process whereProcess = new ProcessBuilder(findIntellijExecutable).start();

                if (whereProcess.waitFor() == 0) {
                    intellijPath = new BufferedReader(new InputStreamReader(whereProcess.getInputStream())).readLine();
                    ideaButton.setDisabled(false);
                } else {
                    intellijPath = findManuallyInstalledIntellijOnWindows();
                    ideaButton.setDisabled(false);
                }
            } else if (UIUtils.isLinux) {
                List<String> whichCommand = new ArrayList<>();
                whichCommand.add("which");
                whichCommand.addAll(manuallyInstalledLinuxIdeaNames);

                Process whichProcess = new ProcessBuilder(whichCommand).start();

                int exitCode = whichProcess.waitFor();

                if (exitCode < manuallyInstalledLinuxIdeaNames.size() && exitCode >= 0) {
                    intellijPath = new BufferedReader(new InputStreamReader(whichProcess.getInputStream())).readLine();
                    ideaButton.setDisabled(false);
                } else {
                    intellijPath = findFlatpakIntellij();
                    intellijIsFlatpak = true;
                    ideaButton.setDisabled(false);
                }
            } else {
                throw new Exception("IntelliJ not found");
            }
        } catch (Exception e) {
            addTooltip(ideaButton, Align.top, TOOLTIP_WIDTH, prop.getProperty("ideaNotFoundTip"));
            ideaButton.setDisabled(true);
        }

        onChange(ideaButton, () -> {
            try {
                if (intellijIsFlatpak) {
                    new ProcessBuilder("flatpak", "run", intellijPath, ".").directory(Gdx.files.absolute(UserData.projectPath).file()).start();
                } else {
                    new ProcessBuilder(intellijPath, ".").directory(Gdx.files.absolute(UserData.projectPath).file()).start();
                }
            } catch (IOException e) {
                ideaButton.setText("WHOOPS");
            }
        });

        //android studio button
        table.row();
        final TextButton androidStudioButton = new TextButton(prop.getProperty("openAndroidStudio"), skin);
        table.add(androidStudioButton);
        addHandListener(androidStudioButton);
        try {
            androidStudioPath = findAndroidStudio();
            androidStudioIsFlatpak = "com.google.AndroidStudio".equals(androidStudioPath);
            androidStudioButton.setDisabled(false);
        } catch (Exception e) {
            addTooltip(androidStudioButton, Align.top, TOOLTIP_WIDTH, prop.getProperty("androidStudioNotFoundTip"));
            androidStudioButton.setDisabled(true);
        }

        onChange(androidStudioButton, () -> {
            try {
                if (androidStudioIsFlatpak) {
                    new ProcessBuilder("flatpak", "run", androidStudioPath, ".")
                        .directory(Gdx.files.absolute(UserData.projectPath).file()).start();
                } else {
                    new ProcessBuilder(androidStudioPath, ".")
                        .directory(Gdx.files.absolute(UserData.projectPath).file()).start();
                }
            } catch (IOException e) {
                androidStudioButton.setText("WHOOPS");
            }
        });

        //exit button
        table.row();
        textButton = new TextButton(prop.getProperty("exit"), skin);
        table.add(textButton);
        addHandListener(textButton);
        onChange(textButton, () -> Gdx.app.exit());
    }

    private static String findManuallyInstalledIntellijOnWindows() throws Exception {
        String programFilesFolder = System.getenv("PROGRAMFILES");
        File jetbrainsFolder = new File(programFilesFolder, "JetBrains");
        File[] ideaFolders = jetbrainsFolder.listFiles((dir, name) -> name.contains("IDEA"));
        if (ideaFolders == null) {
            throw new Exception("IntelliJ not found");
        }

        File intellijFolder = Stream.of(ideaFolders)
            .max(Comparator.comparingLong(File::lastModified))
            .orElseThrow(() -> new Exception("IntelliJ not found"));

        return new File(intellijFolder, "bin/idea64.exe").getAbsolutePath();
    }

    private static String findAndroidStudio() throws Exception {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            String path = findOnPath("where.exe", "studio64.exe", "studio.exe", "studio.bat");
            if (path != null) return path;

            String[] roots = {System.getenv("PROGRAMFILES"), System.getenv("LOCALAPPDATA")};
            for (String root : roots) {
                if (root == null) continue;
                for (String executableName : new String[]{"studio64.exe", "studio.exe"}) {
                    File executable = new File(root, "Android/Android Studio/bin/" + executableName);
                    if (executable.isFile()) return executable.getAbsolutePath();
                }
            }
        } else if (osName.contains("mac")) {
            File executable = new File("/Applications/Android Studio.app/Contents/MacOS/studio");
            if (executable.isFile()) return executable.getAbsolutePath();
        } else if (osName.contains("linux")) {
            String path = findOnPath("which", "studio", "android-studio");
            if (path != null) return path;

            String[] locations = {"/opt/android-studio/bin/studio.sh", "/usr/local/android-studio/bin/studio.sh"};
            for (String location : locations) {
                if (new File(location).isFile()) return location;
            }

            String flatpakId = findFlatpakApplication("com.google.AndroidStudio");
            if (flatpakId != null) {
                return flatpakId;
            }
        }
        throw new Exception("Android Studio not found");
    }

    private static String findOnPath(String command, String... executableNames) throws Exception {
        for (String executableName : executableNames) {
            Process process = new ProcessBuilder(command, executableName).start();
            int ret = process.waitFor();
            if (ret != 0) continue;

            String path = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
            if (path != null && !path.trim().isEmpty()) return path.trim();
        }
        return null;
    }

    private static String findFlatpakApplication(String applicationId) throws Exception {
        Process process = new ProcessBuilder("flatpak", "list", "--app", "--columns=application").start();
        if (process.waitFor() != 0) return null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (applicationId.equals(line.trim())) return applicationId;
        }
        return null;
    }

    @NotNull
    private static String findFlatpakIntellij() throws Exception {
        List<String> findFlatpakIntelliJ = Arrays.asList("flatpak", "list", "--app", "--columns=application");

        Process flatpakProcess = new ProcessBuilder(findFlatpakIntelliJ).start();
        if (flatpakProcess.waitFor() != 0) {
            throw new Exception("Flatpak not found");
        }

        List<String> intellijIds = Arrays.asList("com.jetbrains.IntelliJ-IDEA-Ultimate", "com.jetbrains.IntelliJ-IDEA-Community");
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(flatpakProcess.getInputStream()));

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (intellijIds.contains(line)) {
                return line;
            }
        }
        throw new Exception("Flatpak IntelliJ not installed");
    }

    @Override
    public void captureKeyboardFocus() {

    }
}
