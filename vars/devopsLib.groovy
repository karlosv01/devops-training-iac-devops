def runInDocker(String image, Closure body) {
  docker.image(image).inside {
    body()
  }
}

def composeUpTestDown(Map args = [:]) {
  if (!args.up || !args.test || !args.down) {
    error "composeUpTestDown requiere los argumentos: up, test y down"
  }

  try {
    sh args.up.toString()
    sh args.test.toString()
  } finally {
    sh "${args.down} || true"
  }
}

def readVersion(String versionFile = "VERSION") {
  String version = readFile(file: versionFile).trim()
  if (!version) {
    error "El fichero ${versionFile} existe pero esta vacio"
  }
  return version
}

def setComposeServiceImage(Map args = [:]) {
  String composeFile = args.get("composeFile", "docker-compose.yml").toString()
  String serviceName = args.get("serviceName", "app").toString()
  String image = args.get("image", "").toString().trim()

  if (!image) {
    error "setComposeServiceImage requiere el argumento image"
  }

  List<String> lines = readFile(file: composeFile).split("\\r?\\n", -1) as List<String>
  int servicesIndex = lines.findIndexOf { it.trim() == "services:" }
  if (servicesIndex < 0) {
    error "No se encontro el bloque services: en ${composeFile}"
  }

  int servicesIndent = indentSize(lines[servicesIndex])
  int serviceIndex = -1
  int serviceIndent = -1

  for (int index = servicesIndex + 1; index < lines.size(); index++) {
    String currentLine = lines[index]
    String trimmed = currentLine.trim()

    if (!trimmed || trimmed.startsWith("#")) {
      continue
    }

    int currentIndent = indentSize(currentLine)
    if (currentIndent <= servicesIndent) {
      break
    }

    if (trimmed == "${serviceName}:") {
      serviceIndex = index
      serviceIndent = currentIndent
      break
    }
  }

  if (serviceIndex < 0) {
    error "No se encontro el servicio ${serviceName} dentro de ${composeFile}"
  }

  int serviceEnd = lines.size()
  for (int index = serviceIndex + 1; index < lines.size(); index++) {
    String currentLine = lines[index]
    String trimmed = currentLine.trim()

    if (!trimmed || trimmed.startsWith("#")) {
      continue
    }

    if (indentSize(currentLine) <= serviceIndent) {
      serviceEnd = index
      break
    }
  }

  int imageLineIndex = -1
  int imageIndent = serviceIndent + 2
  for (int index = serviceIndex + 1; index < serviceEnd; index++) {
    String currentLine = lines[index]
    String trimmed = currentLine.trim()

    if (!trimmed || trimmed.startsWith("#")) {
      continue
    }

    int currentIndent = indentSize(currentLine)
    if (currentIndent > serviceIndent && trimmed.startsWith("image:")) {
      imageLineIndex = index
      imageIndent = currentIndent
      break
    }
  }

  String imageLine = "${' ' * imageIndent}image: ${image}"
  if (imageLineIndex >= 0) {
    lines[imageLineIndex] = imageLine
  } else {
    lines.add(serviceIndex + 1, imageLine)
  }

  writeFile(file: composeFile, text: lines.join("\n"))
  echo "Imagen del servicio ${serviceName} actualizada en ${composeFile}: ${image}"
}

private int indentSize(String line) {
  int idx = 0
  while (idx < line.length() && line.charAt(idx) == ' ') {
    idx++
  }
  return idx
}
