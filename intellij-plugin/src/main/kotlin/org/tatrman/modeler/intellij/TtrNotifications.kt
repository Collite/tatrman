package org.tatrman.modeler.intellij

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.tatrman.modeler.intellij.settings.TtrSettingsConfigurable

/**
 * User-facing balloons from the `TTR Modeler` notification group. The
 * missing-Node case is the one real UX wrinkle (architecture §6): it must be
 * actionable, never a silent failure or a stack trace.
 */
object TtrNotifications {
    private const val GROUP_ID = "TTR Modeler"

    /** Node could not be located — show an actionable balloon with "Configure…". */
    fun nodeMissing(project: Project?) {
        group()
            .createNotification(TtrBundle.message("ttr.node.missing"), NotificationType.ERROR)
            .addAction(
                NotificationAction.createSimple(TtrBundle.message("ttr.node.configure")) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, TtrSettingsConfigurable::class.java)
                },
            )
            .notify(project)
    }

    /** Node resolved but below the supported version — advisory warning only. */
    fun nodeTooOld(project: Project?, version: String) {
        group()
            .createNotification(TtrBundle.message("ttr.node.tooOld", version), NotificationType.WARNING)
            .notify(project)
    }

    private fun group() =
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
}
