@echo off
@rem ##########################################################################
@rem
@rem  Synthea launcher for Windows
@rem
@rem ##########################################################################
setlocal EnableDelayedExpansion
SET runTask=run

IF "%~1" == "" (
  @rem Just run Synthea with no args
  gradlew.bat %runTask%
  
) ELSE (
  @rem Running Synthea with arguments
  @rem For simplicity, do nothing and just pass the args to gradle
  SET syntheaArgs= 

  :loop
  if "%~1"=="" goto run
  if "%~1"=="--aml" (
    SET runTask=runAml
  ) else (
    SET syntheaArgs=!syntheaArgs!'%~1',
  )
  shift
  goto loop

  :run
  if "!syntheaArgs!"==" " (
    gradlew.bat !runTask!
  ) else (
    gradlew.bat !runTask! -Params="[!syntheaArgs!]"
  )
)
