import torch
import torch.nn as nn


class SignBiLSTM(nn.Module):
    """
    Bidirectional LSTM 수어 분류 모델

    입력: (batch, seq_len=60, input_size=141)
        * input_size 141 = (왼손 21*3) + (오른손 21*3) + (상체 포즈 5*3)
    출력: (batch, num_classes)
    """
    def __init__(self, input_size: int = 141, hidden_size: int = 128,
                 num_layers: int = 2, num_classes: int = 5,
                 dropout: float = 0.3):
        super().__init__()
        self.hidden_size = hidden_size
        self.num_layers  = num_layers

        # Bidirectional LSTM (batch_first=True)
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )

        self.dropout = nn.Dropout(p=dropout)

        # Bidirectional → hidden_size * 2
        self.fc = nn.Linear(hidden_size * 2, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len, input_size)
        out, (h_n, c_n) = self.lstm(x)

        # h_n shape: (num_layers * 2, batch, hidden_size)
        # 가장 마지막 레이어의 forward 방향과 backward 방향의 최종 상태 결합
        forward_hidden = h_n[-2, :, :]
        backward_hidden = h_n[-1, :, :]
        last = torch.cat((forward_hidden, backward_hidden), dim=1)

        last = self.dropout(last)
        return self.fc(last)             # (batch, num_classes)
