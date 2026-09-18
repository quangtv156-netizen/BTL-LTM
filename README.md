# Barricade Game

Game cờ chiến thuật kiểu Quoridor, chơi online 1vs1 qua mạng LAN/Internet. Client dùng JavaFX, Server dùng Java Socket thuần, dữ liệu người chơi và lịch sử đấu lưu trên MySQL.

## Tính năng

- Đăng nhập / tự động đăng ký nếu tài khoản chưa tồn tại
- Sảnh chờ (lobby): danh sách người chơi online, thách đấu, xem bảng xếp hạng, xem lịch sử đấu
- Chơi 1vs1 theo lượt, mỗi lượt giới hạn 15 giây, hết giờ tự động bị bỏ lượt
- Di chuyển quân hoặc đặt barricade (hàng rào) chặn đường đối thủ, không cho đặt hàng rào chặn hết đường đi
- Highlight các ô có thể di chuyển, hiệu ứng trượt quân mượt khi di chuyển
- Chat trong trận đấu
- Đầu hàng, xin tái đấu (rematch) sau khi kết thúc trận
- Hỗ trợ mất kết nối / kết nối lại giữa trận (15 giây để reconnect trước khi xử thua)
- Lưu điểm số, số trận thắng, lịch sử đấu vào MySQL

## Kiến trúc

    src/
    ├── client/          # Ứng dụng Client (JavaFX)
    │   ├── net/         # Kết nối socket tới server
    │   └── ui/          # Giao diện: đăng nhập, sảnh chờ, bàn chơi
    ├── common/          # Model và giao thức dùng chung giữa Client/Server
    │   ├── message/     # Message + MessageType (giao thức truyền qua Socket)
    │   └── model/       # BoardState, PlayerInfo, Wall
    └── server/          # Server (Java Socket + MySQL)
        ├── model/       # Board (logic bàn cờ), Match, Player
        ├── net/         # ClientHandler (xử lý từng kết nối client)
        └── service/     # AccountService, MatchManager, PersistenceService

Giao tiếp Client-Server qua `ObjectInputStream`/`ObjectOutputStream` (Socket TCP thuần), không dùng framework mạng nào khác.

## Yêu cầu môi trường

- JDK 17 trở lên
- [JavaFX SDK](https://gluonhq.com/products/javafx/) đúng phiên bản JDK đang dùng (chỉ cần cho phía Client)
- MySQL Server (đã cài và đang chạy)
- Driver MySQL Connector/J (đã có sẵn trong thư mục `lib/`)

## Cài đặt

### 1. Chuẩn bị MySQL

Tạo database:

    CREATE DATABASE barricade_game;

Server sẽ tự tạo 2 bảng `users` và `history` khi chạy lần đầu, không cần tạo bảng thủ công.


### 2. Cấu hình JavaFX cho Client

Trong IntelliJ IDEA:
- `File → Project Structure → Libraries → +` → chọn thư mục `lib` bên trong JavaFX SDK đã tải
- `Run → Edit Configurations` → chọn cấu hình `ClientApp` → thêm VM options:

    --module-path "DUONG_DAN_TOI_JAVAFX_SDK\lib" --add-modules javafx.controls

## Chạy chương trình

1. Chạy Server trước: `server.GameServer` (main class), hoặc dùng `run_server.bat`
2. Chạy Client: `client.ClientApp` (main class), hoặc dùng `run_client.bat`
3. Có thể chạy nhiều Client cùng lúc trên cùng máy để test 2 người chơi

## Cấu trúc dữ liệu MySQL

**Bảng `users`**

| Cột          | Kiểu        | Ghi chú             |
|--------------|------       |---------------------|
| username     | VARCHAR(50) | Khóa chính          |
| password     | VARCHAR(100)|                     |
| score        | INT         | Điểm tích lũy       |
| wins         | INT         | Số trận thắng       |
| games_played | INT         | Tổng số trận đã đấu |

**Bảng `history`**

| Cột              | Kiểu        | Ghi chú                 |
|------------------|-------------|-------------------------|
| id               | INT         | Khóa chính, tự tăng     |
| played_at        | DATETIME    | Thời điểm kết thúc trận |
| player1, player2 | VARCHAR(50) | Hai người chơi          |
| winner           | VARCHAR(50) | Người thắng             |

---
