$file = [System.IO.FileInfo]$args[0]
#echo $file.FullName
$filepath = $file.Directory.FullName
#echo $filepath
$filename = $file.BaseName
#echo $filename
$newfilename = $filepath +"\" + $filename + '.yxmd'
#echo $newfilename
$url = "http://localhost:8099/doc/workflow"
Invoke-RestMethod -Method 'Post' -Uri $url -InFile $file.FullName -OutFile $newfilename
& 'C:\Program Files\Alteryx\bin\AlteryxGui.exe' $newfilename 