package com.difocus.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Не блокируем само приложение DI Focus, системный UI и телефонную звонилку
        if (packageName == applicationContext.packageName ||
            packageName.contains("systemui") ||
            packageName.contains("dialer") ||
            packageName.contains("launcher")
        ) {
            return
        }

        // Если активен 5-минутный экстренный выход — пропускаем
        if (FocusRepository.isEmergencyBypassActive(applicationContext)) {
            return
        }

        // Если есть невыполненные задачи на сегодня и открыто запрещенное приложение — перехватываем!
        if (FocusRepository.hasIncompleteTasksToday() && FocusRepository.blockedPackages.contains(packageName)) {
            val lockIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("LOCKED_INTERCEPT", true)
                putExtra("BLOCKED_PACKAGE", packageName)
            }
            startActivity(lockIntent)
        }
    }

    override fun onInterrupt() {}
}
