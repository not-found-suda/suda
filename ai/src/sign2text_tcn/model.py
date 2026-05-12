import math

import torch
import torch.nn as nn


class PositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=5000):
        super().__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model)
        )
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0)
        self.register_buffer("pe", pe)

    def forward(self, x):
        seq_len = x.size(1)
        return x + self.pe[:, :seq_len, :]


class KSLTransformerV6(nn.Module):
    def __init__(
        self,
        input_dim=332,
        num_classes=4,
        d_model=128,
        num_heads=8,
        num_layers=3,
        dim_feedforward=512,
        dropout=0.1,
    ):
        super().__init__()
        self.input_projection = nn.Linear(input_dim, d_model)
        self.pos_encoder = PositionalEncoding(d_model)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=num_heads,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            batch_first=True,
        )
        self.transformer_encoder = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        self.classifier = nn.Sequential(
            nn.Linear(d_model, d_model // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, num_classes),
        )

    def forward(self, x, lengths=None, return_probs=False):
        x = self.input_projection(x)
        x = self.pos_encoder(x)

        padding_mask = None
        if lengths is not None:
            max_len = x.size(1)
            positions = torch.arange(max_len, device=x.device).unsqueeze(0)
            padding_mask = positions >= lengths.unsqueeze(1)

        encoded = self.transformer_encoder(x, src_key_padding_mask=padding_mask)

        if lengths is not None:
            valid_mask = (~padding_mask).unsqueeze(-1).float()
            pooled = (encoded * valid_mask).sum(dim=1) / valid_mask.sum(dim=1).clamp(min=1.0)
        else:
            pooled = encoded.mean(dim=1)

        output = self.classifier(pooled)
        if return_probs:
            return torch.softmax(output, dim=1)
        return output


class KSLMLPV6(nn.Module):
    def __init__(
        self,
        input_dim=332,
        num_classes=4,
        seq_len=30,
        hidden_dim=512,
        dropout=0.2,
    ):
        super().__init__()
        self.seq_len = seq_len
        self.input_dim = input_dim
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.LayerNorm(seq_len * input_dim),
            nn.Linear(seq_len * input_dim, hidden_dim),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim // 2, num_classes),
        )

    def forward(self, x, lengths=None, return_probs=False):
        output = self.classifier(x)
        if return_probs:
            return torch.softmax(output, dim=1)
        return output


class KSLTemporalConvV6(nn.Module):
    def __init__(
        self,
        input_dim=332,
        num_classes=4,
        channels=128,
        dropout=0.2,
    ):
        super().__init__()
        self.feature_extractor = nn.Sequential(
            nn.Conv1d(input_dim, channels, kernel_size=3, padding=1),
            nn.BatchNorm1d(channels),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Conv1d(channels, channels, kernel_size=3, padding=1),
            nn.BatchNorm1d(channels),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Conv1d(channels, channels, kernel_size=3, padding=1),
            nn.BatchNorm1d(channels),
            nn.ReLU(),
        )
        self.classifier = nn.Sequential(
            nn.Linear(channels, channels // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(channels // 2, num_classes),
        )

    def forward(self, x, lengths=None, return_probs=False):
        x = x.transpose(1, 2)
        features = self.feature_extractor(x)
        pooled = features.mean(dim=2)
        output = self.classifier(pooled)
        if return_probs:
            return torch.softmax(output, dim=1)
        return output


def build_ksl_model_v6(
    model_type,
    input_dim=332,
    num_classes=4,
    seq_len=30,
    d_model=128,
    num_heads=8,
    num_layers=3,
):
    model_type = (model_type or "transformer").strip().lower()
    if model_type == "transformer":
        return KSLTransformerV6(
            input_dim=input_dim,
            num_classes=num_classes,
            d_model=d_model,
            num_heads=num_heads,
            num_layers=num_layers,
        )
    if model_type == "mlp":
        return KSLMLPV6(input_dim=input_dim, num_classes=num_classes, seq_len=seq_len)
    if model_type in ("tcn", "conv1d", "temporal_conv"):
        return KSLTemporalConvV6(input_dim=input_dim, num_classes=num_classes, channels=d_model)
    raise ValueError(f"Unsupported model_type={model_type}. Use transformer, mlp, or tcn.")


# Backward-compatible alias for conversion/inference scripts that still import the old name.
KSLTransformerV5 = KSLTransformerV6
