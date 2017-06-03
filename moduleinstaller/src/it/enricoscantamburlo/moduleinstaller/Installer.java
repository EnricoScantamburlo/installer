package it.enricoscantamburlo.moduleinstaller;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.netbeans.api.autoupdate.InstallSupport;
import org.netbeans.api.autoupdate.InstallSupport.Validator;
import org.netbeans.api.autoupdate.OperationContainer;
import static org.netbeans.api.autoupdate.OperationContainer.createForInstall;
import org.netbeans.api.autoupdate.OperationException;
import org.netbeans.api.autoupdate.OperationSupport.Restarter;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateManager;
import org.netbeans.api.autoupdate.UpdateUnit;
import org.netbeans.api.autoupdate.UpdateUnitProvider;
import org.netbeans.api.autoupdate.UpdateUnitProviderFactory;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

public class Installer extends ModuleInstall {

    private static final String MODULE_CNB = "it.enricoscantamburlo.installme";
    private static final String PROP_MODULE_INSTALLED = "moduleInstalled";

    private static final Logger LOGGER = Logger.getLogger(Installer.class.getName());

    @Override
    public void restored() {
        if (!isModuleInstalled()) {
            installModule();
        }
    }

    @NbBundle.Messages({"Installer.progress=Installing module..."})
    private void installModule() {
        ProgressHandle handle = ProgressHandle.createHandle(Bundle.Installer_progress());
        handle.start();
        handle.switchToIndeterminate();
        try {
            // I refresh the module list
            for (UpdateUnitProvider provider : UpdateUnitProviderFactory.getDefault().getUpdateUnitProviders(false)) {
                try {
                    provider.refresh(handle, true);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            List<UpdateUnit> updateUnits = UpdateManager.getDefault().getUpdateUnits();
            UpdateUnit moduleUpdateUnit = null;
            for (UpdateUnit updateUnit : updateUnits) {
                final String codeName = updateUnit.getCodeName();
                //System.out.println("CNB IS: " + codeName);
                if (MODULE_CNB.equals(codeName)) {
                    moduleUpdateUnit = updateUnit;
                    break;
                }
            }

            if (moduleUpdateUnit == null) {
                LOGGER.log(Level.INFO, "Cannot find module " + MODULE_CNB + " to install");
            } else {
                if (moduleUpdateUnit.getInstalled() != null) {
                    setModuleInstalled();
                    return;
                }

                List<UpdateElement> availableUpdates = moduleUpdateUnit.getAvailableUpdates();

                UpdateElement update = pickLatest(availableUpdates);
                if (update == null) {
                    setModuleInstalled();
                    return;
                }
                OperationContainer<InstallSupport> container = createForInstall();
                if (!container.canBeAdded(moduleUpdateUnit, update)) {
                    LOGGER.log(Level.WARNING, "Cannot install module update: " + update);
                    return;
                }
                container.add(Collections.singleton(update));

                InstallSupport support = container.getSupport();
                try {
                    Validator v = support.doDownload(handle, true, true);
                    InstallSupport.Installer i = support.doValidate(v, handle);
                    Restarter r = support.doInstall(i, handle);
                    if (r != null) {
                        support.doRestartLater(r);
                        setModuleInstalled();
                    }
                } catch (OperationException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            }
        } finally {
            handle.finish();
        }
    }

    private UpdateElement pickLatest(List<UpdateElement> availableUpdates) {
        if (availableUpdates == null || availableUpdates.isEmpty()) {
            return null;
        }

        // they are already sorted
        return availableUpdates.get(availableUpdates.size() - 1);
    }

    private void setModuleInstalled() {
        prefs().putBoolean(PROP_MODULE_INSTALLED, true);
    }

    private boolean isModuleInstalled() {
        return prefs().getBoolean(PROP_MODULE_INSTALLED, false);
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(Installer.class);
    }
}
