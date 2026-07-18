## Python AI Environment

The trained AI models are already included:

```text
AIModel/base/new/trained_model.zip
AIModel/adversarial/new/trained_model.zip
```

Before running an AI-controlled simulation for the first time, create a local
virtual environment inside `AIModel`.

Windows:

```powershell
cd AIModel
py -3.11 -m venv env
.\env\Scripts\python.exe -m pip install --upgrade pip setuptools wheel
.\env\Scripts\python.exe -m pip install -r requirements.txt
cd ..
```

macOS/Linux:

```bash
cd AIModel
python3 -m venv env
./env/bin/python -m pip install --upgrade pip setuptools wheel
./env/bin/python -m pip install -r requirements.txt
cd ..
```

Java automatically uses the local virtual environment when it exists:

```text
Windows:     AIModel/env/Scripts/python.exe
macOS/Linux: AIModel/env/bin/python
```
