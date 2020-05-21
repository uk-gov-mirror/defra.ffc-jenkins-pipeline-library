package uk.gov.defra.ffc

import uk.gov.defra.ffc.Helm
import uk.gov.defra.ffc.Utils

class Tests implements Serializable {
  static def runTests(ctx, projectName, serviceName, buildNumber, identityTag) {
    try {
      ctx.sh('mkdir -p test-output')
      ctx.sh('chmod 777 test-output')
      ctx.sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml run $serviceName")
    } finally {
      ctx.sh("docker-compose -p $projectName-$identityTag-$buildNumber -f docker-compose.yaml -f docker-compose.test.yaml down -v")
    }
  }

  static def lintHelm(ctx, chartName) {
    Helm.addHelmRepo(ctx, 'ffc-public', ctx.HELM_CHART_REPO_PUBLIC)
    ctx.sh("helm dependency update ./helm/$chartName")
    ctx.sh("helm lint ./helm/$chartName")
  }

  static def createJUnitReport(ctx) {
    ctx.junit('test-output/junit.xml')
  }

  static def deleteOutput(ctx, containerImage, containerWorkDir) {
    // clean up files created by node/ubuntu user that cannot be deleted by jenkins. Note: uses global environment variable
    ctx.sh("[ -d \"$ctx.WORKSPACE/test-output\" ] && docker run --rm -u node --mount type=bind,source='$ctx.WORKSPACE/test-output',target=/$containerWorkDir/test-output $containerImage rm -rf test-output/*")
  }

  static def analyseCode(ctx, sonarQubeEnv, sonarScanner, params) {
    def scannerHome = tool sonarScanner
    ctx.withSonarQubeEnv(sonarQubeEnv) {
      def args = ''
      params.each { param ->
        args = args + " -D$param.key=$param.value"
      }

      ctx.sh("$scannerHome/bin/sonar-scanner$args")
    }
  }

  static def waitForQualityGateResult(ctx, timeoutInMinutes) {
    ctx.timeout(time: timeoutInMinutes, unit: 'MINUTES') {
      def qualityGateResult = waitForQualityGate()
      if (qualityGateResult.status != 'OK') {
        ctx.error("Pipeline aborted due to quality gate failure: ${qualityGateResult.status}")
      }
    }
  }
}