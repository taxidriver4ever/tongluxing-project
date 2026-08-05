class LocationSnapshot {
  const LocationSnapshot(this.latitude, this.longitude);

  final double latitude;
  final double longitude;

  static LocationSnapshot? current;
}
