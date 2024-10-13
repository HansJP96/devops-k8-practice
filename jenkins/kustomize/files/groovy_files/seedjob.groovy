def seedJob = 'seed_pipeline_job'

pipelineJob(seedJob) {
    description('Pipeline Job que ejecuta stages para crear otros trabajos y carpetas en Jenkins.')
	
	properties {
        buildDiscarder {
            strategy {
                logRotator {
                    numToKeepStr('10')   
                    daysToKeepStr('7')  
                    artifactNumToKeepStr('5') 
                    artifactDaysToKeepStr('14') 
                }
            }
        }
    }
	
    definition {
        cps {
            script(new File('/home/seed/basePipeline.groovy').text) // Carga el script desde la ruta especificada
            sandbox()
        }
    }
    authenticationToken('tokenSeedBuild')
}