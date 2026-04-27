import { Ionicons } from "@expo/vector-icons";
import { Audio, ResizeMode, Video } from "expo-av";
import { CameraCapturedPicture, CameraView, useCameraPermissions } from "expo-camera";
import * as FileSystem from "expo-file-system/legacy";
import * as Location from "expo-location";
import * as MediaLibrary from "expo-media-library";
import { getApp, getApps, initializeApp } from "firebase/app";
import { getDatabase, off, onValue, push, ref, serverTimestamp, set } from "firebase/database";
import { NavigationContainer } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Animated,
  Easing,
  FlatList,
  Image,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";

type RecordType = "photo" | "video" | "audioNote";
type SyncStatus = "synced" | "pending";

type SurveyRecord = {
  recordId: string;
  userId: string;
  type: RecordType;
  latitude: number;
  longitude: number;
  timestamp: string;
  localPath: string;
  note: string;
  createdAt: number;
  syncStatus: SyncStatus;
};

function toSafeRecord(input: unknown): SurveyRecord | null {
  if (!input || typeof input !== "object") return null;
  const raw = input as Partial<SurveyRecord> & { createdAt?: unknown };
  const lat = Number(raw.latitude);
  const lng = Number(raw.longitude);
  const createdAt = Number(raw.createdAt);
  const typeOk = raw.type === "photo" || raw.type === "video" || raw.type === "audioNote";
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || !typeOk || typeof raw.localPath !== "string") {
    return null;
  }
  const safeType = raw.type as RecordType;
  return {
    recordId: typeof raw.recordId === "string" ? raw.recordId : `fallback-${Date.now()}`,
    userId: typeof raw.userId === "string" ? raw.userId : USER_ID,
    type: safeType,
    latitude: lat,
    longitude: lng,
    timestamp: typeof raw.timestamp === "string" ? raw.timestamp : new Date().toISOString(),
    localPath: raw.localPath,
    note: typeof raw.note === "string" ? raw.note : "",
    createdAt: Number.isFinite(createdAt) ? createdAt : Date.now(),
    syncStatus: raw.syncStatus === "pending" ? "pending" : "synced",
  };
}

type RootStackParamList = {
  Tabs: undefined;
  Detail: { record: SurveyRecord };
};

type TabParamList = {
  Home: undefined;
  Capture: undefined;
  Audio: undefined;
  History: undefined;
};

const BRAND = {
  bg: "#030712",
  surface: "#111827",
  softSurface: "#1f2937",
  text: "#f9fafb",
  muted: "#9ca3af",
  accent: "#22d3ee",
  accentStrong: "#06b6d4",
  warning: "#fb7185",
  border: "#374151",
};

const ICONS: Record<RecordType, keyof typeof Ionicons.glyphMap> = {
  photo: "image-outline",
  video: "videocam-outline",
  audioNote: "mic-outline",
};

const firebaseConfig = {
  apiKey: "AIzaSyCrEJztcjMU-6u4TIaAFA2rquJGTw_Ji8A",
  authDomain: "mobileappcw2-24607.firebaseapp.com",
  databaseURL: "https://mobileappcw2-24607-default-rtdb.firebaseio.com",
  projectId: "mobileappcw2-24607",
  storageBucket: "mobileappcw2-24607.firebasestorage.app",
  messagingSenderId: "91017208247",
  appId: "1:91017208247:android:da192cbd41f05259740832",
};

const firebaseApp = getApps().length ? getApp() : initializeApp(firebaseConfig);
const database = getDatabase(firebaseApp);
const recordsRef = ref(database, "records");
const LOCAL_MEDIA_DIR = `${FileSystem.documentDirectory}geocapture`;
const USER_ID = "student_demo_user";

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tabs = createBottomTabNavigator<TabParamList>();

class AppErrorBoundary extends React.Component<{ children: React.ReactNode }, { hasError: boolean; message: string }> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, message: "" };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, message: error?.message ?? "Unknown runtime error" };
  }

  render() {
    if (this.state.hasError) {
      return (
        <SafeAreaView style={[styles.screen, { justifyContent: "center" }]}>
          <View style={styles.card}>
            <Text style={styles.cardTitle}>App runtime error</Text>
            <Text style={styles.errorText}>{this.state.message}</Text>
            <Text style={styles.emptyText}>Please reload the app after checking this message.</Text>
          </View>
        </SafeAreaView>
      );
    }
    return this.props.children;
  }
}

async function ensureMediaDirectory(): Promise<void> {
  const info = await FileSystem.getInfoAsync(LOCAL_MEDIA_DIR);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(LOCAL_MEDIA_DIR, { intermediates: true });
  }
}

async function getCurrentCoordinates(): Promise<{ latitude: number; longitude: number }> {
  const { status } = await Location.requestForegroundPermissionsAsync();
  if (status !== "granted") {
    throw new Error("Location permission is required.");
  }
  const current = await Location.getCurrentPositionAsync({
    accuracy: Location.Accuracy.Balanced,
  });
  return { latitude: current.coords.latitude, longitude: current.coords.longitude };
}

function formatDate(value: number | string): string {
  return new Date(value).toLocaleString();
}

async function uploadMetadata(record: Omit<SurveyRecord, "recordId">): Promise<void> {
  const newRef = push(recordsRef);
  await set(newRef, {
    ...record,
    recordId: newRef.key ?? `fallback-${Date.now()}`,
    createdAt: serverTimestamp(),
  });
}

function useRecords() {
  const [records, setRecords] = useState<SurveyRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadingGuard = setTimeout(() => {
      setLoading(false);
      setError((prev) => prev ?? "Realtime DB response timeout. Check network or Firebase rules.");
    }, 8000);

    const handler = (snapshot: { val: () => Record<string, SurveyRecord> | null }) => {
      const payload = snapshot.val();
      const parsed = payload
        ? Object.values(payload)
            .map((item) => toSafeRecord(item))
            .filter((item): item is SurveyRecord => item !== null)
            .sort((a, b) => b.createdAt - a.createdAt)
        : [];
      setRecords(parsed);
      setError(null);
      setLoading(false);
    };
    const cancel = (dbError: Error) => {
      setError(dbError.message || "Unable to load from Firebase.");
      setLoading(false);
    };
    onValue(recordsRef, handler, cancel);
    return () => {
      clearTimeout(loadingGuard);
      off(recordsRef);
    };
  }, []);

  return { records, loading, error };
}

function ScreenTitle({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <View style={styles.header}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>
    </View>
  );
}

function HomeScreen({ navigation }: { navigation: { navigate: (screen: keyof TabParamList) => void } }) {
  const { records, loading, error } = useRecords();
  const latest = records[0];
  const latestHasValidGps =
    !!latest && Number.isFinite(Number(latest.latitude)) && Number.isFinite(Number(latest.longitude));

  return (
    <SafeAreaView style={styles.screen}>
      <ScreenTitle title="GeoCapture Logbook" subtitle="Field Survey & Evidence Logging" />
      <View style={styles.hero}>
        <Text style={styles.heroTitle}>Professional Local-first Workflow</Text>
        <Text style={styles.heroText}>Capture evidence media with GPS, sync only metadata to Firebase Realtime DB.</Text>
      </View>

      <View style={styles.kpiRow}>
        <View style={styles.kpiCard}>
          <Text style={styles.kpiValue}>{records.length}</Text>
          <Text style={styles.kpiLabel}>Total Records</Text>
        </View>
        <View style={styles.kpiCard}>
          <Text style={styles.kpiValue}>{records.filter((item) => item.type === "photo").length}</Text>
          <Text style={styles.kpiLabel}>Photos</Text>
        </View>
        <View style={styles.kpiCard}>
          <Text style={styles.kpiValue}>{records.filter((item) => item.type === "video").length}</Text>
          <Text style={styles.kpiLabel}>Videos</Text>
        </View>
      </View>
      <View style={styles.quickRow}>
        <Pressable style={styles.quickButton} onPress={() => navigation.navigate("Capture")}>
          <Ionicons name="camera-outline" size={18} color={BRAND.accent} />
          <Text style={styles.quickButtonText}>Test Camera</Text>
        </Pressable>
        <Pressable style={styles.quickButton} onPress={() => navigation.navigate("Audio")}>
          <Ionicons name="mic-outline" size={18} color={BRAND.accent} />
          <Text style={styles.quickButtonText}>Test Audio</Text>
        </Pressable>
        <Pressable style={styles.quickButton} onPress={() => navigation.navigate("History")}>
          <Ionicons name="time-outline" size={18} color={BRAND.accent} />
          <Text style={styles.quickButtonText}>View History</Text>
        </Pressable>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Location Preview</Text>
        {loading ? (
          <ActivityIndicator color={BRAND.accent} />
        ) : error ? (
          <Text style={styles.errorText}>{error}</Text>
        ) : latestHasValidGps && latest ? (
          <>
            <View style={styles.locationCard}>
              <Ionicons name="location" size={20} color={BRAND.accent} />
              <Text style={styles.locationText}>Lat: {latest.latitude.toFixed(6)}</Text>
              <Text style={styles.locationText}>Lng: {latest.longitude.toFixed(6)}</Text>
            </View>
            <Pressable
              style={styles.outlineButton}
              onPress={() => Linking.openURL(`https://maps.google.com/?q=${latest.latitude},${latest.longitude}`)}
            >
              <Text style={styles.outlineButtonText}>Open in Google Maps</Text>
            </Pressable>
            <Text style={styles.mapHint}>
              Latest: {latest.type.toUpperCase()} at {latest.latitude.toFixed(5)}, {latest.longitude.toFixed(5)}
            </Text>
          </>
        ) : (
          <Text style={styles.emptyText}>No records yet. Capture your first survey evidence.</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

function CaptureScreen() {
  const cameraRef = useRef<CameraView | null>(null);
  const [permission, requestPermission] = useCameraPermissions();
  const [mediaPermission, requestMediaPermission] = MediaLibrary.usePermissions();
  const [cameraMode, setCameraMode] = useState<RecordType>("photo");
  const [isRecording, setIsRecording] = useState(false);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  const permissionReady = permission?.granted && mediaPermission?.granted;

  const requestAll = async () => {
    await requestPermission();
    await requestMediaPermission();
    await Location.requestForegroundPermissionsAsync();
    await Audio.requestPermissionsAsync();
  };

  const saveAndSync = async (uri: string, type: RecordType) => {
    setBusy(true);
    try {
      await ensureMediaDirectory();
      const extension = type === "photo" ? "jpg" : "mp4";
      const targetPath = `${LOCAL_MEDIA_DIR}/${type}-${Date.now()}.${extension}`;
      await FileSystem.copyAsync({ from: uri, to: targetPath });
      await MediaLibrary.saveToLibraryAsync(targetPath);
      const coords = await getCurrentCoordinates();
      await uploadMetadata({
        userId: USER_ID,
        type,
        latitude: coords.latitude,
        longitude: coords.longitude,
        timestamp: new Date().toISOString(),
        localPath: targetPath,
        note,
        createdAt: Date.now(),
        syncStatus: "synced",
      });
      setNote("");
      Alert.alert("Success", `${type} record saved.`);
    } catch (error) {
      Alert.alert("Error", error instanceof Error ? error.message : "Unknown error");
    } finally {
      setBusy(false);
    }
  };

  const takePhoto = async () => {
    if (!cameraRef.current) return;
    const photo: CameraCapturedPicture = await cameraRef.current.takePictureAsync({ quality: 0.8 });
    await saveAndSync(photo.uri, "photo");
  };

  const startVideo = async () => {
    if (!cameraRef.current || isRecording) return;
    setIsRecording(true);
    try {
      const video = await cameraRef.current.recordAsync({ maxDuration: 60 });
      if (video?.uri) {
        await saveAndSync(video.uri, "video");
      }
    } finally {
      setIsRecording(false);
    }
  };

  return (
    <SafeAreaView style={styles.screen}>
      <ScreenTitle title="Capture Studio" subtitle="Camera + GPS + Note metadata" />
      {!permissionReady ? (
        <View style={styles.card}>
          <Text style={styles.emptyText}>Camera, audio, and location permissions are required.</Text>
          <Pressable style={styles.primaryButton} onPress={requestAll}>
            <Text style={styles.primaryButtonText}>Grant Permissions</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <CameraView ref={cameraRef} style={styles.camera} mode={cameraMode === "photo" ? "picture" : "video"} facing="back" />
          <View style={styles.card}>
            <View style={styles.segmentWrap}>
              <Pressable style={[styles.segment, cameraMode === "photo" && styles.segmentActive]} onPress={() => setCameraMode("photo")}>
                <Text style={styles.segmentText}>Photo</Text>
              </Pressable>
              <Pressable style={[styles.segment, cameraMode === "video" && styles.segmentActive]} onPress={() => setCameraMode("video")}>
                <Text style={styles.segmentText}>Video</Text>
              </Pressable>
            </View>
            <TextInput
              style={styles.input}
              value={note}
              onChangeText={setNote}
              placeholder="Evidence note"
              placeholderTextColor={BRAND.muted}
            />
            {busy ? (
              <ActivityIndicator color={BRAND.accent} />
            ) : (
              <Pressable
                style={[styles.primaryButton, isRecording && { backgroundColor: BRAND.warning }]}
                onPress={cameraMode === "photo" ? takePhoto : isRecording ? () => cameraRef.current?.stopRecording() : startVideo}
              >
                <Text style={styles.primaryButtonText}>
                  {cameraMode === "photo" ? "Capture Photo" : isRecording ? "Stop Recording" : "Start Recording"}
                </Text>
              </Pressable>
            )}
          </View>
        </>
      )}
    </SafeAreaView>
  );
}

function AudioScreen() {
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [audioUri, setAudioUri] = useState<string | null>(null);
  const [player, setPlayer] = useState<Audio.Sound | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    return () => {
      if (player) {
        player.unloadAsync();
      }
    };
  }, [player]);

  const start = async () => {
    setBusy(true);
    try {
      await Audio.requestPermissionsAsync();
      await Audio.setAudioModeAsync({ allowsRecordingIOS: true, playsInSilentModeIOS: true });
      const { recording: active } = await Audio.Recording.createAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
      setRecording(active);
    } finally {
      setBusy(false);
    }
  };

  const stop = async () => {
    if (!recording) return;
    setBusy(true);
    try {
      await recording.stopAndUnloadAsync();
      const uri = recording.getURI();
      if (!uri) throw new Error("Audio URI missing.");
      await ensureMediaDirectory();
      const targetPath = `${LOCAL_MEDIA_DIR}/audio-${Date.now()}.m4a`;
      await FileSystem.copyAsync({ from: uri, to: targetPath });
      const coords = await getCurrentCoordinates();
      await uploadMetadata({
        userId: USER_ID,
        type: "audioNote",
        latitude: coords.latitude,
        longitude: coords.longitude,
        timestamp: new Date().toISOString(),
        localPath: targetPath,
        note,
        createdAt: Date.now(),
        syncStatus: "synced",
      });
      setAudioUri(targetPath);
      setNote("");
      setRecording(null);
    } catch (error) {
      Alert.alert("Error", error instanceof Error ? error.message : "Unknown error");
    } finally {
      setBusy(false);
    }
  };

  const play = async () => {
    if (!audioUri) return;
    if (player) {
      await player.unloadAsync();
    }
    const { sound } = await Audio.Sound.createAsync({ uri: audioUri });
    setPlayer(sound);
    await sound.playAsync();
  };

  return (
    <SafeAreaView style={styles.screen}>
      <ScreenTitle title="Audio Note Lab" subtitle="Voice memo with location stamp" />
      <View style={styles.card}>
        <TextInput style={styles.input} value={note} onChangeText={setNote} placeholder="Audio note description" placeholderTextColor={BRAND.muted} />
        <Pressable style={styles.primaryButton} onPress={recording ? stop : start} disabled={busy}>
          <Text style={styles.primaryButtonText}>{recording ? "Stop & Save Audio" : "Start Recording"}</Text>
        </Pressable>
        <Pressable style={styles.outlineButton} onPress={play} disabled={!audioUri || busy}>
          <Text style={styles.outlineButtonText}>Play Last Recording</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function TimelineItem({ item, index, onPress }: { item: SurveyRecord; index: number; onPress: () => void }) {
  const anim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(anim, {
      toValue: 1,
      duration: 380,
      delay: index * 70,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [anim, index]);

  return (
    <Animated.View style={{ opacity: anim, transform: [{ translateY: anim.interpolate({ inputRange: [0, 1], outputRange: [16, 0] }) }] }}>
      <Pressable style={styles.timelineCard} onPress={onPress}>
        <View style={styles.timelineLine} />
        <View style={styles.timelineDot} />
        <View style={styles.timelineContent}>
          <View style={styles.timelineHeader}>
            <Ionicons name={ICONS[item.type]} size={18} color={BRAND.accent} />
            <Text style={styles.timelineType}>{item.type.toUpperCase()}</Text>
            <Text style={styles.timelineTime}>{formatDate(item.createdAt)}</Text>
          </View>
          <Text style={styles.timelineMeta}>{item.latitude.toFixed(5)}, {item.longitude.toFixed(5)}</Text>
          <Text style={styles.timelineMeta}>{item.note || "No note"}</Text>
        </View>
      </Pressable>
    </Animated.View>
  );
}

function HistoryScreen({ navigation }: { navigation: { navigate: (screen: "Detail", params: { record: SurveyRecord }) => void } }) {
  const { records, loading } = useRecords();

  return (
    <SafeAreaView style={styles.screen}>
      <ScreenTitle title="Timeline History" subtitle="Realtime metadata stream" />
      {loading ? (
        <ActivityIndicator color={BRAND.accent} />
      ) : (
        <FlatList
          data={records}
          keyExtractor={(item) => item.recordId}
          contentContainerStyle={{ paddingBottom: 24, gap: 10 }}
          renderItem={({ item, index }) => (
            <TimelineItem item={item} index={index} onPress={() => navigation.navigate("Detail", { record: item })} />
          )}
        />
      )}
    </SafeAreaView>
  );
}

function DetailScreen({ route }: { route: { params: { record: SurveyRecord } } }) {
  const { record } = route.params;
  const [exists, setExists] = useState<boolean | null>(null);
  const [sound, setSound] = useState<Audio.Sound | null>(null);

  useEffect(() => {
    FileSystem.getInfoAsync(record.localPath).then((info) => setExists(info.exists));
    return () => {
      if (sound) sound.unloadAsync();
    };
  }, [record.localPath, sound]);

  const playAudio = async () => {
    if (!exists) return;
    const { sound: newSound } = await Audio.Sound.createAsync({ uri: record.localPath });
    setSound(newSound);
    await newSound.playAsync();
  };

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView>
        <ScreenTitle title="Record Detail" subtitle={record.recordId} />
        <View style={styles.card}>
          <Text style={styles.detailText}>Type: {record.type}</Text>
          <Text style={styles.detailText}>Created: {formatDate(record.createdAt)}</Text>
          <Text style={styles.detailText}>GPS: {record.latitude}, {record.longitude}</Text>
          <Text style={styles.detailText}>Note: {record.note || "No note"}</Text>
          {exists === false ? (
            <Text style={[styles.detailText, { color: BRAND.warning }]}>Local file missing on this device.</Text>
          ) : record.type === "photo" ? (
            <Image source={{ uri: record.localPath }} style={styles.preview} />
          ) : record.type === "video" ? (
            <Video source={{ uri: record.localPath }} style={styles.preview} useNativeControls resizeMode={ResizeMode.CONTAIN} />
          ) : (
            <Pressable style={styles.primaryButton} onPress={playAudio}>
              <Text style={styles.primaryButtonText}>Play Audio</Text>
            </Pressable>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function TabsRoot() {
  return (
    <Tabs.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: styles.tabBar,
        tabBarActiveTintColor: BRAND.accent,
        tabBarInactiveTintColor: BRAND.muted,
        tabBarIcon: ({ color, size }) => {
          const iconMap: Record<string, keyof typeof Ionicons.glyphMap> = {
            Home: "home-outline",
            Capture: "camera-outline",
            Audio: "mic-outline",
            History: "time-outline",
          };
          return <Ionicons name={iconMap[route.name]} size={size} color={color} />;
        },
      })}
    >
      <Tabs.Screen name="Home" component={HomeScreen} />
      <Tabs.Screen name="Capture" component={CaptureScreen} />
      <Tabs.Screen name="Audio" component={AudioScreen} />
      <Tabs.Screen name="History" component={HistoryScreen} />
    </Tabs.Navigator>
  );
}

export default function AppEnhanced() {
  return (
    <AppErrorBoundary>
      <SafeAreaProvider>
        <NavigationContainer>
          <Stack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: BRAND.bg } }}>
            <Stack.Screen name="Tabs" component={TabsRoot} />
            <Stack.Screen name="Detail" component={DetailScreen} />
          </Stack.Navigator>
        </NavigationContainer>
      </SafeAreaProvider>
    </AppErrorBoundary>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BRAND.bg,
    paddingHorizontal: 16,
  },
  header: {
    marginTop: 8,
    marginBottom: 14,
  },
  title: {
    color: BRAND.text,
    fontSize: 28,
    fontWeight: "700",
  },
  subtitle: {
    marginTop: 4,
    color: BRAND.muted,
  },
  hero: {
    backgroundColor: "#0f172a",
    borderColor: BRAND.border,
    borderWidth: 1,
    borderRadius: 18,
    padding: 16,
    marginBottom: 12,
  },
  heroTitle: {
    color: BRAND.text,
    fontWeight: "700",
    marginBottom: 8,
  },
  heroText: {
    color: "#cbd5e1",
    lineHeight: 20,
  },
  kpiRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 12,
  },
  kpiCard: {
    flex: 1,
    backgroundColor: BRAND.surface,
    borderRadius: 14,
    padding: 10,
    borderWidth: 1,
    borderColor: BRAND.border,
  },
  kpiValue: {
    color: BRAND.accent,
    fontSize: 20,
    fontWeight: "700",
  },
  kpiLabel: {
    color: BRAND.muted,
    fontSize: 12,
  },
  card: {
    backgroundColor: BRAND.surface,
    borderWidth: 1,
    borderColor: BRAND.border,
    borderRadius: 16,
    padding: 14,
    gap: 12,
  },
  quickRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 12,
  },
  quickButton: {
    flex: 1,
    backgroundColor: BRAND.surface,
    borderWidth: 1,
    borderColor: BRAND.border,
    borderRadius: 12,
    paddingVertical: 10,
    alignItems: "center",
    gap: 4,
  },
  quickButtonText: {
    color: BRAND.text,
    fontSize: 12,
    fontWeight: "600",
  },
  cardTitle: {
    color: BRAND.text,
    fontWeight: "700",
  },
  locationCard: {
    backgroundColor: "#0b1220",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BRAND.border,
    padding: 12,
    gap: 6,
  },
  locationText: {
    color: BRAND.text,
  },
  mapHint: {
    color: BRAND.muted,
    fontSize: 12,
  },
  emptyText: {
    color: BRAND.muted,
    lineHeight: 20,
  },
  errorText: {
    color: BRAND.warning,
    lineHeight: 20,
  },
  camera: {
    width: "100%",
    height: 320,
    borderRadius: 16,
    overflow: "hidden",
    marginBottom: 10,
  },
  segmentWrap: {
    flexDirection: "row",
    gap: 8,
  },
  segment: {
    flex: 1,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: BRAND.border,
    paddingVertical: 8,
    alignItems: "center",
  },
  segmentActive: {
    backgroundColor: BRAND.softSurface,
    borderColor: BRAND.accentStrong,
  },
  segmentText: {
    color: BRAND.text,
    fontWeight: "600",
  },
  input: {
    borderColor: BRAND.border,
    borderWidth: 1,
    borderRadius: 10,
    color: BRAND.text,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: "#030712",
  },
  primaryButton: {
    backgroundColor: BRAND.accent,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: "center",
  },
  primaryButtonText: {
    color: "#083344",
    fontWeight: "700",
  },
  outlineButton: {
    borderWidth: 1,
    borderColor: BRAND.accent,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: "center",
  },
  outlineButtonText: {
    color: BRAND.accent,
    fontWeight: "700",
  },
  timelineCard: {
    backgroundColor: BRAND.surface,
    borderColor: BRAND.border,
    borderWidth: 1,
    borderRadius: 14,
    padding: 12,
    flexDirection: "row",
  },
  timelineLine: {
    width: 2,
    backgroundColor: "#334155",
    marginRight: 10,
    borderRadius: 10,
  },
  timelineDot: {
    width: 10,
    height: 10,
    borderRadius: 999,
    backgroundColor: BRAND.accent,
    marginRight: 10,
    marginTop: 6,
  },
  timelineContent: {
    flex: 1,
    gap: 4,
  },
  timelineHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  timelineType: {
    color: BRAND.text,
    fontWeight: "700",
  },
  timelineTime: {
    marginLeft: "auto",
    color: BRAND.muted,
    fontSize: 12,
  },
  timelineMeta: {
    color: "#d1d5db",
  },
  detailText: {
    color: "#d1d5db",
    lineHeight: 20,
  },
  preview: {
    width: "100%",
    height: 280,
    borderRadius: 12,
    backgroundColor: "#000",
  },
  tabBar: {
    backgroundColor: BRAND.surface,
    borderTopColor: BRAND.border,
    height: 62,
    paddingBottom: 6,
    paddingTop: 6,
  },
});
