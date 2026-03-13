# 鍚庣鎺ュ彛鏂囨。锛圵S 闊抽涓婁紶 + SSE 缁撴灉鎺ㄩ€侊級

## 1. 鎬昏
1. 涓婅閫氶亾锛歐ebSocket锛堜簩杩涘埗闊抽 + 鏂囨湰鎺у埗锛?
2. 涓嬭閫氶亾锛歋SE锛堣瘑鍒粨鏋溿€佺炕璇戙€佹憳瑕併€侀棶绛斻€侀敊璇級
3. 闊抽缁熶竴瑙勬牸锛歚PCM_SIGNED / S16_LE / 16000Hz / Mono`
4. 鍏抽敭绾︽潫锛?
    1. 闊抽浜岃繘鍒跺抚涓嶈蛋 JSON
    2. 涓嶈繘琛?Base64
    3. 浜岃繘鍒跺抚鍙寘鍚?PCM 瀛楄妭锛屾棤鑷畾涔夊ご

## 2. 閫氶亾鍦板潃
1. WebSocket 闊抽涓婁紶锛歚ws://{host}/ws/audio`
2. SSE 缁撴灉鎺ㄩ€侊細`http://{host}/sse/audio?translationRecordId={translationRecordId}`

## 3. 杩炴帴涓庢椂搴?
1. 瀹㈡埛绔缓绔?WebSocket 杩炴帴
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）
6. 瀹㈡埛绔彂閫?`stop` 鏂囨湰娑堟伅缁撴潫
7. 鏈嶅姟绔叧闂?SSE 鎴栧厑璁哥瓑寰呬笅涓€娆?`start`

## 4. 闊抽鍙傛暟瑙勮寖
1. 閲囨牱鐜囷細16000 Hz
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）
6. 姣忓抚閲囨牱鐐癸細`16000 * 0.02 = 320`
7. 姣忓抚瀛楄妭鏁帮細`320 * 2 = 640 bytes`
8. 鍗曟牱鏈紪鐮侊細Int16 灏忕搴?
9. 鏍锋湰鑼冨洿锛歚[-32768, 32767]`

## 5. WebSocket锛堜笂琛岋級

### 5.1 start 娑堟伅
鐢ㄩ€旓細澹版槑浼氳瘽涓庨煶棰戝弬鏁?
鏂瑰悜锛欳lient 鈫?Server
绫诲瀷锛歍ext(JSON)

```json
{
  "type": "start",
  "translationRecordId": "8f8f2d4a-9c55-4c52-8f08-1b7f76c6c001",
  "sourceLang": "zh",
  "targetLang": "en",
  "audio": {
    "codec": "pcm_s16le",
    "sampleRate": 16000,
    "bitsPerSample": 16,
    "channels": 1,
    "signed": true,
    "littleEndian": true,
    "chunkDurationMs": 20,
    "bytesPerChunk": 640
  }
}
```

瀛楁璇存槑锛?
1. `type`锛氬浐瀹?`start`
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）

鏈嶅姟绔鐞嗭細
1. 鏍￠獙鍙傛暟鏄惁鏀寔
2. 鍒濆鍖栦細璇濅笌缂撳啿鍖?
3. 鍑嗗鎺ュ彈浜岃繘鍒跺抚

### 5.2 stop 娑堟伅
鐢ㄩ€旓細缁撴潫褰撳墠浼氳瘽
鏂瑰悜锛欳lient 鈫?Server
绫诲瀷锛歍ext(JSON)

```json
{
  "type": "stop",
  "translationRecordId": "8f8f2d4a-9c55-4c52-8f08-1b7f76c6c001"
}
```

瀛楁璇存槑锛?
1. `type`锛氬浐瀹?`stop`
2. `translationRecordId`锛氬搴斿紑濮嬩細璇?

鏈嶅姟绔鐞嗭細
1. 缁撴潫璇嗗埆
2. 杈撳嚭鏈€缁堢粨鏋滐紙鍙€夛級
3. 閲婃斁璧勬簮

### 5.3 ping 蹇冭烦锛堝彲閫夛級
鐢ㄩ€旓細杩炴帴淇濇椿
鏂瑰悜锛欳lient 鈫?Server
绫诲瀷锛歍ext(JSON)

```json
{ "type": "ping", "ts": 1710000000000 }
```

瀛楁璇存槑锛?
1. `type`锛氬浐瀹?`ping`
2. `ts`锛氬鎴风鏃堕棿鎴筹紙姣锛?

鏈嶅姟绔缓璁細
1. 鍙拷鐣ユ垨鐢ㄤ簬鐩戞祴寤惰繜
2. 濡傞渶鍙洖澶?`pong`锛堜笉寮哄埗锛?

### 5.4 闊抽甯э紙浜岃繘鍒讹級
鐢ㄩ€旓細瀹炴椂闊抽鏁版嵁
鏂瑰悜锛欳lient 鈫?Server
绫诲瀷锛欱inary

鏍煎紡锛?
```
[PCM S16_LE bytes only]
```

绾︽潫锛?
1. 涓嶅寘鍚?JSON
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）

## 6. SSE锛堜笅琛岋級

### 6.1 SSE 杩炴帴
URL锛歚http://{host}/sse/audio?translationRecordId={translationRecordId}`

鏈嶅姟绔搷搴斿ご寤鸿锛?
1. `Content-Type: text/event-stream`
2. `Cache-Control: no-cache`
3. `Connection: keep-alive`
4. 鍙嶅悜浠ｇ悊闇€鍏抽棴缂撳啿锛堝 Nginx `proxy_buffering off`锛?

### 6.2 SSE 娑堟伅鏍煎紡
鏈嶅姟绔€氳繃 SSE `data:` 鍙戦€?JSON 瀛楃涓诧細

```
data: {"type":"partial","text":"浣犲ソ"}

```

瀹㈡埛绔細瀵?`data` 杩涜 JSON 瑙ｆ瀽锛屽緱鍒扮粺涓€鐨?`ServerMessage`銆?

## 7. 鏈嶅姟绔繑鍥炴秷鎭紙SSE data锛?

### 7.1 partial 瀛楀箷
鐢ㄩ€旓細瀹炴椂涓存椂瀛楀箷

```json
{
  "type": "partial",
  "text": "浣犲ソ涓栫晫"
}
```

### 7.2 final 瀛楀箷
鐢ㄩ€旓細鏈€缁堝瓧骞曠墖娈?

```json
{
  "type": "final",
  "text": "浣犲ソ涓栫晫"
}
```

### 7.3 translation 缈昏瘧
鐢ㄩ€旓細瀹炴椂缈昏瘧

```json
{
  "type": "translation",
  "text": "Hello world"
}
```

### 7.4 summary 鎽樿
鐢ㄩ€旓細浼氳瘽鎽樿

```json
{
  "type": "summary",
  "text": "浼氳涓昏璁ㄨ浜?.."
}
```

### 7.5 qa 闂瓟
鐢ㄩ€旓細闂瓟缁撴灉

```json
{
  "type": "qa",
  "question": "浠婂ぉ鐨勯噸鐐规槸浠€涔堬紵",
  "answer": "閲嶇偣鏄?.."
}
```

### 7.6 error 閿欒
鐢ㄩ€旓細涓氬姟閿欒鎻愮ず

```json
{
  "type": "error",
  "message": "Unsupported audio format"
}
```

## 8. 閿欒鐮佸缓璁紙鍙€夛級
濡傛灉闇€瑕佺粨鏋勫寲閿欒锛屽彲鎵╁睍涓猴細

```json
{
  "type": "error",
  "code": "AUDIO_UNSUPPORTED",
  "message": "Unsupported audio format"
}
```

寤鸿閿欒鐮侊細
1. `AUDIO_UNSUPPORTED`
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）

## 9. 鏈嶅姟绔鐞嗘祦绋嬪缓璁?
1. 鏀跺埌 `start` 鈫?鏍￠獙鍙傛暟 鈫?鍒濆鍖栦細璇濈姸鎬?
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）

## 10. 鎬ц兘涓庣ǔ瀹氭€у缓璁?
1. 璇嗗埆寮曟搸搴旀敮鎸佹祦寮忚緭鍏?
2. 闊抽甯ц繃杞芥椂鍙涪寮冩棫甯?
3. 杩炴帴鏂紑鍙洿鎺ユ竻鐞?session
4. SSE 鏂紑鍙嚜鍔ㄩ噸杩烇紙鏀寔 `Last-Event-ID` 瑙嗗叿浣撳疄鐜帮級

## 11. 鏈€灏忓彲鐢ㄦ祦绋嬶紙MVP锛?
1. 寤虹珛 WebSocket
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）
6. 瀹㈡埛绔彂閫?`stop`

## 12. 绀轰緥浜や簰搴忓垪
1. Client 鈫?WS: `start`
2. `translationRecordId`：UUID，客户端生成
3. `sourceLang`：源语言（如 `zh` / `en`，可选，默认 `zh`）
4. `targetLang`：目标语言（如 `en` / `zh`，可选，默认 `en`）
5. `audio`：音频固定规格（服务端校验）
6. Client 鈫?WS: `stop`









