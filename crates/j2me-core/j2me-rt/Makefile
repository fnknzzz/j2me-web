JSR_184 ?= 1
JSR_239 ?= 1
JSR_082 ?= 1

SRC_DIRS=cldc1.1.1 vm midp custom

ifeq ($(JSR_256),1)
SRC_DIRS += jsr-256
endif

ifeq ($(JSR_082),1)
SRC_DIRS += jsr-082-compat
JSR_082_API_SRCS = \
 jsr-082/javax/bluetooth/BluetoothConnectionException.java \
 jsr-082/javax/bluetooth/BluetoothStateException.java \
 jsr-082/javax/bluetooth/DataElement.java \
 jsr-082/javax/bluetooth/DeviceClass.java \
 jsr-082/javax/bluetooth/DiscoveryListener.java \
 jsr-082/javax/bluetooth/L2CAPConnection.java \
 jsr-082/javax/bluetooth/L2CAPConnectionNotifier.java \
 jsr-082/javax/bluetooth/ServiceRecord.java \
 jsr-082/javax/bluetooth/ServiceRegistrationException.java \
 jsr-082/javax/bluetooth/UUID.java
endif

ifeq ($(JSR_179),1)
SRC_DIRS += jsr-179
endif

ifeq ($(JSR_184),1)
SRC_DIRS += jsr-184
endif

# Mascot Capsule Micro3D is a vendor API, not part of JSR-184 (M3G).
# The directory currently preserves the unadapted Android implementation as
# reference material. Enable it only after its platform dependencies have been
# replaced with CLDC/Rust bridge implementations.
MASCOT_CAPSULE ?= 1
ifeq ($(MASCOT_CAPSULE),1)
SRC_DIRS += mascot-capsule
endif

ifeq ($(JSR_239),1)
SRC_DIRS += jsr-239
endif

SRCS=$(shell find $(SRC_DIRS) -name '*.java') $(JSR_082_API_SRCS)
EXTRA=$(shell find . -name '*.png' 2>/dev/null) $(shell find . -name '*.bin' 2>/dev/null) $(shell find l10n -name '*.json' 2>/dev/null)

MODE ?= debug
DEBUG_JAR = classes.debug.jar
PROD_JAR = classes.prod.jar

.PHONY: classes.jar

build/.compiled: Makefile $(SRCS) $(EXTRA)
	rm -rf build build-src
	mkdir build build-src
	$(foreach dir,$(SRC_DIRS),cp -a $(dir)/. build-src/;)
	$(if $(strip $(JSR_082_API_SRCS)),mkdir -p build-src/javax/bluetooth && cp $(JSR_082_API_SRCS) build-src/javax/bluetooth/)
	find ./build-src -name '*.java' > build-srcs.txt
	javac -cp build-src -g:none -source 8 -target 8 -bootclasspath "" -extdirs "" -d ./build @build-srcs.txt || javac -cp build-src -g:none --release 8 -d ./build @build-srcs.txt
	rm -rf build-src
	$(if $(strip $(EXTRA)),for file in $(EXTRA); do mkdir -p "build/$$(dirname "$$file")"; cp "$$file" "build/$$file"; done)
	touch build/.compiled

classes.debug.jar: build/.compiled $(EXTRA)
	cd build && jar cf0 ../$(DEBUG_JAR) *

classes.prod.jar: build/.compiled $(EXTRA)
	cd build && jar cMf ../$(PROD_JAR) *

classes.jar: classes.$(MODE).jar
	cp classes.$(MODE).jar classes.jar

clean:
	rm -f `find . -name "*.jar" -or -name "*~"`
	rm -f build-srcs.txt
	rm -rf sootOutput build build-src
