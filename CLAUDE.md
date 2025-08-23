# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a React Native library package that provides access to Android call logs. The library follows a standard React Native native module architecture with JavaScript bridge to Android native code.

## Architecture

The library consists of three main layers:

1. **JavaScript API Layer** (`callLogs.js`, `index.js`): Provides the main JavaScript interface with methods `load()`, `loadAll()`, and filtering capabilities
2. **TypeScript Definitions** (`index.d.ts`): Contains TypeScript interfaces for CallLog, CallFilter, and callType enum
3. **Android Native Module** (`android/src/main/java/com/wscodelabs/callLogs/`):
   - `CallLogModule.java`: Main native module that queries Android's CallLog.Calls content provider
   - `CallLogPackage.java`: React Native package registration

## Key Components

### JavaScript Bridge (`callLogs.js`)
- Handles parameter normalization (arrays, timestamps, types)
- Converts filter objects to native-compatible format
- Bridges to `NativeModules.CallLogs`

### Native Module (`CallLogModule.java`)
- Implements three main methods: `load()`, `loadAll()`, `loadWithFilter()`
- Queries Android's CallLog.Calls content provider
- Supports filtering by timestamp range, phone numbers, and call types
- Returns structured call log data with phone number, duration, name, timestamp, type, etc.

### Type System (`index.d.ts`)
- Defines CallLog interface with all call properties
- CallFilter interface for filtering options
- callType enum with all Android call types (INCOMING, OUTGOING, MISSED, etc.)

## Development Commands

Since this is a library package, development primarily involves:

- **Example App**: Use `cd Example && npm install` to set up the example application
- **Testing**: The example app in `Example/` directory serves as the main test environment
- **Android Development**: Native Android code is in `android/` directory

## Common Development Tasks

When modifying the library:

1. **Adding new call log fields**: Update both `CallLogModule.java` (native side) and `index.d.ts` (TypeScript definitions)
2. **Adding new filter options**: Update `CallFilter` interface in `index.d.ts` and filtering logic in `CallLogModule.java`
3. **Testing changes**: Use the Example app which demonstrates typical usage with permissions handling

## Important Notes

- This library is Android-only (no iOS implementation)
- Requires `READ_CALL_LOG` permission
- The native module queries the system call log database directly
- All timestamps are handled as strings in the native bridge for precision
- Call types are mapped from Android integer constants to readable string enums