def validateDirStructure (File dir, List<String> expectedLevels, int currentLevel, List<String> errors) {
	if (dir.name == '.git') {
        return
    }

	def subDirs = dir.listFiles().findAll { it.isDirectory() }
	def expectedLevelName = expectedLevels[currentLevel]

	if (currentLevel < expectedLevels.size()) {
		if (subDirs.size() > 0) {
			subDirs.each { subDir ->
				validateDirStructure(subDir, expectedLevels, currentLevel + 1, errors)
			}
		} else if (currentLevel < expectedLevels.size() - 1) {
			errors.add("Faltan subdirectorios en el nivel ${currentLevel + 1} (${expectedLevelName}) en la ruta: ${dir.absolutePath}")
		}
	}
}

def createFoldersAndJobs = '''
def repoDir = new File(SEED_JOB.lastBuild.checkouts[0].workspace)

def createFolderIfNotExists(String folderPath, String textDescription) {
    def jenkinsInstance = jenkins.model.Jenkins.instance

    if (!jenkinsInstance.getItemByFullName(folderPath)) {
        folder(folderPath) {
            description(textDescription)
        }
    }
}

repoDir.eachDir { proyectoDir ->
    if (proyectoDir.name == ".git") {
        return // Omit the .git directory
    }
    proyectoDir.eachDir { appDir ->
        appDir.eachDir { ambienteDir ->
            ambienteDir.eachDir { jobDir ->
                def jenkinsfilePath = "${jobDir.absolutePath}/Jenkinsfile"
                def configGroovyPath = "${jobDir.absolutePath}/PipelineConfig.groovy"

                createFolderIfNotExists("${proyectoDir.name}", "Carpeta para ${proyectoDir.name}")
                createFolderIfNotExists("${proyectoDir.name}/${appDir.name}", "Carpeta para ${appDir.name} en ${proyectoDir.name}")
                createFolderIfNotExists("${proyectoDir.name}/${appDir.name}/${ambienteDir.name}", "Carpeta para ${ambienteDir.name} en ${appDir.name}/${proyectoDir.name}")
				createFolderIfNotExists("${proyectoDir.name}/${appDir.name}/${ambienteDir.name}/${jobDir.name}", "Carpeta para Pipeline ${jobDir.name}")
            }
        }
    }
}
'''

pipeline {
	agent any
    
	environment {
		REPO_URL = 'https://github.com/HansJP96/repoJobs.git'
		BRANCH = 'main'
	}

	stages {
		stage('Setup') {
			steps {
				script {
					echo 'Preparando el entorno...'
				}
				script {
					// Clonar el repositorio
					checkout([$class: 'GitSCM',
							  branches: [[name: "*/${env.BRANCH}"]],
							  userRemoteConfigs: [[url: env.REPO_URL]]])
				}
			}
		}
		
		stage('Validate Directory Structure') {
			steps {
				script {
					def workspaceDir = pwd()
					echo "L as  ${workspaceDir}"
					def errors = []
					
					def rootDir = new File(workspaceDir)
					def expectedLevels = ['Proyecto', 'App', 'Ambiente', 'Job']
					validateDirStructure(rootDir, expectedLevels, 0, errors)
					if (errors.size() > 0) {
						errors.each { error ->
							echo error
						}
						error("La estructura de directorios no cumple con el patrón esperado.")
					} else {
						echo "La estructura de directorios es válida."
					}
				}
			}
		}
		
		stage('Create Jobs and Folders') {
			steps {
				script {
					jobDsl scriptText: createFoldersAndJobs
				}
			}
		}
		
		stage('Find and Execute Groovy Scripts') {
            steps {
                script {
                    // Buscar todos los archivos .groovy en subdirectorios
                    def groovyFiles = findFiles(glob: "**/*.groovy")

                    groovyFiles.each { groovyFile ->
                        // Usar la cláusula dir() para cambiar el directorio de trabajo
                        def path = groovyFile.path.tokenize('/')[0..-2].join('/')

                        dir("${path}") {
                        
                            echo "Ejecutando script: ${groovyFile}"
                            
                            def scriptContent = readFile(groovyFile.name)
        
                            scriptContent = scriptContent.replaceAll(/DIRECTORY_PATH/, "${path}")
                            scriptContent = scriptContent.replaceAll(/JENKINSFILE_PATH/, "${path}/Jenkinsfile")

                            // Ejecutar el archivo .groovy usando Job DSL
                            jobDsl scriptText: scriptContent
                        }
                    }
                }
            }
        }
	}
	post {
		always {
			echo 'Proceso terminado.'
		}
	}
}