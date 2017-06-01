package amidst;

import java.util.Optional;

import amidst.documentation.AmidstThread;
import amidst.documentation.CalledOnlyBy;
import amidst.documentation.NotThreadSafe;
import amidst.fragment.FragmentManager;
import amidst.fragment.layer.LayerBuilder;
import amidst.gui.license.LicenseWindow;
import amidst.gui.main.Actions;
import amidst.gui.main.MainWindow;
import amidst.gui.main.MainWindowDialogs;
import amidst.gui.main.PerMainWindowInjector;
import amidst.gui.main.UpdatePrompt;
import amidst.gui.main.viewer.BiomeSelection;
import amidst.gui.main.viewer.PerViewerFacadeInjector;
import amidst.gui.main.viewer.ViewerFacade;
import amidst.gui.main.viewer.Zoom;
import amidst.gui.profileselect.ProfileSelectWindow;
import amidst.mojangapi.MojangApi;
import amidst.mojangapi.file.DotMinecraftDirectoryNotFoundException;
import amidst.mojangapi.file.LauncherProfile;
import amidst.mojangapi.file.MinecraftInstallation;
import amidst.mojangapi.file.PlayerInformationCache;
import amidst.mojangapi.file.PlayerInformationProvider;
import amidst.mojangapi.minecraftinterface.local.LocalMinecraftInterfaceCreationException;
import amidst.mojangapi.world.SeedHistoryLogger;
import amidst.mojangapi.world.World;
import amidst.mojangapi.world.WorldBuilder;
import amidst.settings.biomeprofile.BiomeProfileDirectory;
import amidst.threading.ThreadMaster;

@NotThreadSafe
public class PerApplicationInjector {
	private final AmidstMetaData metadata;
	private final AmidstSettings settings;
	private final PlayerInformationProvider playerInformationProvider;
	private final SeedHistoryLogger seedHistoryLogger;
	private final MinecraftInstallation minecraftInstallation;
	private final Optional<LauncherProfile> preferredLauncherProfile;
	private final WorldBuilder worldBuilder;
	private final MojangApi mojangApi;
	private final BiomeProfileDirectory biomeProfileDirectory;
	private final ThreadMaster threadMaster;
	private final LayerBuilder layerBuilder;
	private final Zoom zoom;
	private final FragmentManager fragmentManager;
	private final BiomeSelection biomeSelection;
	private final Application application;

	@CalledOnlyBy(AmidstThread.EDT)
	public PerApplicationInjector(CommandLineParameters parameters, AmidstMetaData metadata, AmidstSettings settings)
			throws DotMinecraftDirectoryNotFoundException,
			LocalMinecraftInterfaceCreationException {
		this.metadata = metadata;
		this.settings = settings;
		this.playerInformationProvider = new PlayerInformationCache();
		this.seedHistoryLogger = SeedHistoryLogger.from(parameters.seedHistoryFile);
		this.minecraftInstallation = MinecraftInstallation
				.newLocalMinecraftInstallation(parameters.dotMinecraftDirectory);
		this.preferredLauncherProfile = minecraftInstallation
				.tryReadLauncherProfile(parameters.minecraftJarFile, parameters.minecraftJsonFile);
		this.worldBuilder = new WorldBuilder(playerInformationProvider, seedHistoryLogger);
		this.mojangApi = MojangApi.from(minecraftInstallation, preferredLauncherProfile, worldBuilder);
		this.biomeProfileDirectory = BiomeProfileDirectory.create(parameters.biomeProfilesDirectory);
		this.threadMaster = new ThreadMaster();
		this.layerBuilder = new LayerBuilder();
		this.zoom = new Zoom(settings.maxZoom);
		this.fragmentManager = new FragmentManager(layerBuilder.getConstructors(), layerBuilder.getNumberOfLayers());
		this.biomeSelection = new BiomeSelection();
		this.application = new Application(
				mojangApi,
				this::createNoisyUpdatePrompt,
				this::createSilentUpdatePrompt,
				this::createMainWindow,
				this::createProfileSelectWindow,
				this::createLicenseWindow);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private UpdatePrompt createNoisyUpdatePrompt(MainWindowDialogs dialogs) {
		return UpdatePrompt.from(metadata.getVersion(), threadMaster.getWorkerExecutor(), dialogs, false);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private UpdatePrompt createSilentUpdatePrompt() {
		return UpdatePrompt.from(metadata.getVersion(), threadMaster.getWorkerExecutor(), null, true);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private MainWindow createMainWindow() {
		return new PerMainWindowInjector(
				application,
				metadata,
				settings,
				mojangApi,
				biomeProfileDirectory,
				this::createViewerFacade,
				threadMaster).getMainWindow();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private ProfileSelectWindow createProfileSelectWindow() {
		return new ProfileSelectWindow(
				application,
				metadata,
				threadMaster.getWorkerExecutor(),
				minecraftInstallation,
				mojangApi,
				settings);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private LicenseWindow createLicenseWindow() {
		return new LicenseWindow(metadata);
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private ViewerFacade createViewerFacade(World world, Actions actions) {
		return new PerViewerFacadeInjector(
				settings,
				threadMaster.getWorkerExecutor(),
				zoom,
				layerBuilder,
				fragmentManager,
				biomeSelection,
				world,
				actions).getViewerFacade();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	public Application getApplication() {
		return application;
	}
}
