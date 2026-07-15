' Kylin SQL Developer - Silently run kylin-sql.bat with hidden console.
' This VBS file itself has no console window, so the cmd window never flashes.
Set fso = CreateObject("Scripting.FileSystemObject")
appHome = fso.GetParentFolderName(WScript.ScriptFullName)
batFile = fso.BuildPath(appHome, "kylin-sql.bat")
If fso.FileExists(batFile) Then
    CreateObject("WScript.Shell").Run "cmd /c """ & batFile & """", 0, False
End If
