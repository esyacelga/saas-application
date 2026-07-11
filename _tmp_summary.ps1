Get-ChildItem 'C:\Respos\own-aplications\billing-service\target\surefire-reports' -Filter 'TEST-*.xml' | ForEach-Object {
    $x = [xml](Get-Content $_.FullName -Raw)
    [pscustomobject]@{
        Name     = $_.Name
        Tests    = $x.testsuite.tests
        Failures = $x.testsuite.failures
        Errors   = $x.testsuite.errors
    }
} | Format-Table -AutoSize -Wrap
