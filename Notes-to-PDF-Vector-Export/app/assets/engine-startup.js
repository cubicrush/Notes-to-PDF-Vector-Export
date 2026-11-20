// Keep this loader readable by older Android System WebView versions.
(function () {
    var moduleScript = document.createElement('script');
    var reported = false;
    function reportFailure() {
        if (reported || typeof window.exportNote === 'function') return;
        reported = true;
        if (window.NotesApp) window.NotesApp.startupFailed();
    }
    if (!('noModule' in moduleScript)) {
        reportFailure();
        return;
    }
    // Module dependency syntax errors can reach the window instead of the script element.
    window.addEventListener('error', reportFailure, true);
    moduleScript.onerror = reportFailure;
    moduleScript.type = 'module';
    moduleScript.src = '/engine.js';
    document.head.appendChild(moduleScript);
}());
