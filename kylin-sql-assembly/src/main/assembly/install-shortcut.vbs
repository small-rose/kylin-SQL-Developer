' Install Kylin SQL Developer shortcut to Start Menu.
' Double-click this script from the app installation directory.
' Icon: logo/kylin.ico
' Target: kylin-sql.vbs (silent launcher, no console flash)

Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

appHome = fso.GetParentFolderName(WScript.ScriptFullName)
targetVbs = fso.BuildPath(appHome, "kylin-sql.vbs")
targetBat = fso.BuildPath(appHome, "kylin-sql.bat")
iconPath  = fso.BuildPath(appHome, "logo\kylin.ico")
startMenu = shell.SpecialFolders("Programs")
shortcutPath = fso.BuildPath(startMenu, "Kylin SQL Developer.lnk")

if Not fso.FileExists(targetVbs) Then
    if Not fso.FileExists(targetBat) Then
        MsgBox "Not found: kylin-sql.vbs or kylin-sql.bat" & vbCrLf & _
               "Please run this script from the Kylin SQL installation directory.", _
               vbExclamation, "Kylin SQL Developer"
        WScript.Quit 1
    End If
    targetVbs = targetBat
End If

Set shortcut = shell.CreateShortcut(shortcutPath)
shortcut.TargetPath = "wscript.exe"
shortcut.Arguments = """" & targetVbs & """"
shortcut.WorkingDirectory = appHome
shortcut.Description = "Kylin SQL Developer - PL/SQL formatter and database tool"
If fso.FileExists(iconPath) Then
    shortcut.IconLocation = iconPath & ", 0"
End If
shortcut.Save

MsgBox "Shortcut created:" & vbCrLf & shortcutPath, _
       vbInformation, "Kylin SQL Developer"
