import os
import subprocess
import platform

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")


def generate_zone_config_dto(output_dir, package_name):
    """ZoneConfig에서 서버에 필요한 필드만 추출하여 ZoneConfigDto 생성

    클라이언트 ZoneConfig 중 서버에서 필요한 필드:
    - zoneName: 존 이름
    - mineralPerHour: 시간당 일반 자원 수확량
    - mineralRarePerHour: 시간당 레어 자원 수확량
    - mineralExoticPerHour: 시간당 엑조틱 자원 수확량
    - mineralDarkPerHour: 시간당 다크 자원 수확량

    WaveConfig, EnemyShipConfig 등 전투 관련 설정은 클라 전용
    """

    server_fields = [
        {'name': 'zoneName', 'type': 'String'},
        {'name': 'clearMineral', 'type': 'Float'},
        {'name': 'clearMineralRare', 'type': 'Float'},
        {'name': 'clearMineralExotic', 'type': 'Float'},
        {'name': 'clearMineralDark', 'type': 'Float'},
        {'name': 'mineralPerHour', 'type': 'Float'},
        {'name': 'mineralRarePerHour', 'type': 'Float'},
        {'name': 'mineralExoticPerHour', 'type': 'Float'},
        {'name': 'mineralDarkPerHour', 'type': 'Float'},
    ]

    java_class_name = "ZoneConfigData"

    java_code = f"package {package_name};\n\n"
    java_code += "import lombok.AllArgsConstructor;\n"
    java_code += "import lombok.Builder;\n"
    java_code += "import lombok.Data;\n"
    java_code += "import lombok.NoArgsConstructor;\n"
    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {java_class_name}\n"
    java_code += " * Auto-generated from Unity C# ZoneConfig class (server-required fields only)\n"
    java_code += " */\n"
    java_code += "@Data\n"
    java_code += "@NoArgsConstructor\n"
    java_code += "@Builder\n"
    java_code += "@AllArgsConstructor\n"
    java_code += f"public class {java_class_name} {{\n"

    for field in server_fields:
        java_code += f"    private {field['type']} {field['name']};\n"

    java_code += "}\n"

    output_file_path = os.path.join(output_dir, f"{java_class_name}.java")
    os.makedirs(output_dir, exist_ok=True)

    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(java_code)

    print(f"Generated: {output_file_path}")
    print(f"  Fields: {', '.join(f['name'] for f in server_fields)}")

    return output_file_path


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, r"../../src/main/java/com/bk/sbs/dto")
    package_name = "com.bk.sbs.dto"

    print("Generating ZoneConfigDto (server-required fields only)")
    print("="*50)

    output_file = generate_zone_config_dto(output_dir, package_name)

    if output_file:
        open_file_location(output_file)
