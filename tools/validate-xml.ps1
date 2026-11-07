$root = "D:\Users\Administrator\Documents\deepseek-harness\default-workspace\halo-lite-preview"
$files = Get-ChildItem -Path $root -Recurse -Include *.xml -File
$bad = 0
foreach ($f in $files) {
  try {
    $t = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $x = New-Object System.Xml.XmlDocument
    $x.LoadXml($t)
    "OK   " + $f.FullName.Substring($root.Length + 1)
  } catch {
    $bad++
    "FAIL " + $f.FullName.Substring($root.Length + 1) + " :: " + $_.Exception.Message
  }
}
"bad=" + $bad